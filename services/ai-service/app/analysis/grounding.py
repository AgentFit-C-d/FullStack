import hashlib
import json
import re
import unicodedata
from bisect import bisect_right

from .contracts import empty_profile, profile_array_limit, validate_analysis_result, validate_provider_output
from .layout_quotes import table_quote_index, table_quote_spans
from .secret_guard import assert_no_secrets

PROMPT_VERSION = "extract-2.4"
_UNCERTAIN = re.compile(r"사용하지|제외|미정|후보|검토(?:합니다|한다| 중| 대상|할| 예정|$)|예정|향후|2차|다음 단계|계획(?:입니다|이다| 중| 단계|하고|합니다|한다|할|이|$)|일 수도|할까요|또는|중 하나|택일|둘 중|아직|not\s+(?:use|using)|candidate|future|consider|planned|undecided|\bor\b", re.IGNORECASE)
_GENERIC_HEADING = re.compile(r"^(?:개발 계획|프로젝트 계획|기획서|프로젝트 기획서|개발 기획서)$")
_OTHER = re.compile(r"다른 프로젝트|참고 사례|이전 프로젝트|other project|reference example", re.IGNORECASE)
_NONE = re.compile(r"없(?:습|음|다)|사용 안|연동 안|none|not required", re.IGNORECASE)
_SELECTION = re.compile(r"확정|단일|하나만|오직|\bonly\b|\bsole\b|\bexclusive\b", re.IGNORECASE)
_LABELS = {"project_name": "프로젝트 이름", "project_type": "프로젝트 유형", "domain": "분야", "frontend": "프론트엔드", "backend": "백엔드", "ai": "AI", "database": "데이터베이스", "deployment": "배포 환경", "features": "기능", "external_integrations": "외부 연동"}
_GENERIC_FIELD_VALUES = {
    "frontend": {"frontend", "프론트엔드"},
    "backend": {"backend", "백엔드"},
    "ai": {"ai", "인공지능"},
    "database": {"database", "db", "데이터베이스"},
    "deployment": {"deployment", "배포", "배포 환경"},
    "features": {
        "feature", "features", "기능", "기능 제공", "서비스 제공", "관리 기능",
        "커뮤니티", "커뮤니티 제공", "커뮤니티 기능", "게시판", "게시판 기능",
        "대시보드", "ai", "인공지능", "ai 기능", "인공지능 기능",
    },
    "external_integrations": {"external integration", "external integrations", "외부 연동"},
}
_AI_TECHNOLOGY = re.compile(
    r"\b(?:api|sdk|gpt|chatgpt|claude|gemini|solar|openai|langchain|llama|llm|"
    r"mistral|anthropic|upstage|bedrock|vertex|cohere|hugging\s*face|embedding|model|"
    r"bert|t5|roberta|electra|transformer|tensorflow|pytorch)\b",
    re.IGNORECASE,
)

def _normalized_text_with_offsets(value):
    normalized, offsets = [], []
    for offset, character in enumerate(value):
        for normalized_character in unicodedata.normalize("NFKC", character).casefold():
            category = unicodedata.category(normalized_character)
            if normalized_character.isspace() or category.startswith(("Z", "P")) or category == "Cf":
                continue
            normalized.append(normalized_character)
            offsets.append(offset)
    return "".join(normalized), offsets


def _is_generic_field_value(field, value):
    if not isinstance(value, str):
        return False
    normalized, _ = _normalized_text_with_offsets(value)
    generic_values = _GENERIC_FIELD_VALUES.get(field, set())
    return normalized in {_normalized_text_with_offsets(name)[0] for name in generic_values}


def _separate_ai_capabilities(items):
    """Keep AI product capabilities in features, not in the technology field."""
    separated = []
    for item in items:
        if item["field"] != "ai" or not isinstance(item["value"], list):
            separated.append(item)
            continue
        values = [value for value in item["value"] if not _is_generic_field_value("ai", value)]
        capabilities = [value for value in values if not _AI_TECHNOLOGY.search(value)]
        technologies = [value for value in values if value not in capabilities]
        if technologies:
            separated.append({**item, "value": technologies})
        if capabilities:
            separated.append({**item, "field": "features", "value": capabilities, "usages": []})
    return separated


def _normalized_spans(text, quote):
    normalized_text, offsets = _normalized_text_with_offsets(text)
    normalized_quote, _ = _normalized_text_with_offsets(quote)
    if len(normalized_quote) < 4:
        return []
    spans, start = [], 0
    while (found := normalized_text.find(normalized_quote, start)) >= 0:
        spans.append((offsets[found], offsets[found + len(normalized_quote) - 1] + 1))
        start = found + 1
    return spans


def _spans(text, quote, table_index=None):
    exact = [(match.start(), match.end()) for match in re.finditer(re.escape(quote), text)]
    normalized = _normalized_spans(text, quote)
    matches = exact or normalized
    return (matches or sorted(set(table_quote_spans(quote, table_index)))) if table_index is not None else matches


def related_value_spans(text, values):
    """Return source locations for every candidate value, without claiming quote validation.

    Callers must keep these candidates unresolved. This only gives a reviewer a
    related PDF page when the AI's longer quote could not be re-located.
    """
    spans = []
    for value in values:
        if not isinstance(value, str) or not value.strip():
            return []
        matches = _spans(text, value)
        if not matches:
            return []
        spans.extend(matches)
    return sorted(set(spans))


def _unique(values):
    seen, result = set(), []
    for value in values:
        key = json.dumps(value, ensure_ascii=False, sort_keys=True)
        if key not in seen:
            seen.add(key)
            result.append(value)
    return result


def _split_array_candidates(candidates):
    """Keep each detected technology or feature independently reviewable."""
    expanded = []
    projected_count = sum(
        len(candidate["proposedValue"])
        if isinstance(candidate["proposedValue"], list) and candidate["proposedValue"]
        else 1
        for candidate in candidates
    )
    if projected_count > 200:
        return candidates
    for candidate in candidates:
        value = candidate["proposedValue"]
        if not isinstance(value, list) or len(value) <= 1:
            expanded.append(candidate)
            continue
        for item in value:
            usages = [usage for usage in candidate.get("usages", []) if usage["value"] == item]
            matching_evidence = [
                evidence
                for evidence in candidate["evidence"]
                if item.casefold() in evidence["quote"].casefold()
            ]
            item_evidence = (matching_evidence or candidate["evidence"])[:1]
            identity = json.dumps(
                [candidate["id"], item, item_evidence, usages],
                ensure_ascii=False,
                sort_keys=True,
            )
            split_candidate = {key: value for key, value in candidate.items() if key != "usages"}
            split_candidate.update({
                "id": hashlib.sha256(identity.encode()).hexdigest()[:32],
                "proposedValue": [item],
                "evidence": item_evidence,
            })
            if usages:
                split_candidate["usages"] = usages
            expanded.append(split_candidate)
    return expanded


def _purpose_role(field, purpose):
    roles = {
        "ai": {"generation": r"답변|생성|요약|추론|generation|language model|\bllm\b", "embedding": r"임베딩|embedding", "workflow": r"오케스트레이션|워크플로|orchestration|workflow"},
        "deployment": {"frontend": r"프론트엔드|frontend|front-end", "backend": r"백엔드|backend|back-end|API 서버", "ai": r"AI 서버|모델 서버|AI service"},
        "database": {"relational": r"관계형|relational", "graph": r"그래프|graph", "vector": r"벡터|vector", "cache": r"캐시|cache"},
        "backend": {"api": r"API 서버|웹 서버|HTTP 서버", "ai": r"AI 서버|모델 서버|추론 서버"},
    }.get(field, {})
    matches = [role for role, pattern in roles.items() if re.search(pattern, purpose, re.IGNORECASE)]
    return matches[0] if len(matches) == 1 else None


def _separate_purposes(field, values, usages):
    roles = {value: set() for value in values}
    for usage in usages:
        role = _purpose_role(field, usage["purpose"])
        if usage["value"] in roles and role:
            roles[usage["value"]].add(role)
    return all(roles.values()) and all(not roles[left].intersection(roles[right]) for index, left in enumerate(values) for right in values[index + 1:])


def validate_grounding(output, document, provider, model, latency_ms=0, *, input_format=None):
    output = validate_provider_output(output)
    assert_no_secrets(output)
    profile, candidates = empty_profile(), []
    states = dict.fromkeys(profile, "unknown")
    seen = set()
    table_index = table_quote_index(document.text) if input_format == 'original-pdf' else None
    sections, section_offsets, headings, offset = [], [], [], 0
    for line in document.text.splitlines(keepends=True):
        match = re.match(r"^\s*(#{1,6})\s+(.+)", line)
        if match:
            level = len(match[1])
            headings = [(depth, title) for depth, title in headings if depth < level]
            headings.append((level, "" if _GENERIC_HEADING.fullmatch(match[2].strip()) else match[2]))
            sections.append(" ".join(title for _, title in headings)); section_offsets.append(offset)
        offset += len(line)
    def context_at(start, end):
        line_start = document.text.rfind("\n", 0, start) + 1
        line_end = document.text.find("\n", end)
        context = document.text[line_start:line_end if line_end >= 0 else len(document.text)]
        if table_index:
            context = ' '.join(heading for begin, finish, heading in table_index['contexts'] if begin <= start < finish) + ' ' + context
        section_index = bisect_right(section_offsets, start) - 1
        return (sections[section_index] + " " if section_index >= 0 else "") + context

    def location(start, end):
        page = next((page.number for page in document.pages if page.start <= start < page.end), 1)
        return {"documentId": document.document_id, "start": start, "end": end, "page": page, "quote": document.text[start:min(end, start + 1000)]}

    items = _separate_ai_capabilities(output["items"])
    technology_names = {part.strip() for item in items if item["field"] in {"frontend", "backend", "ai", "database", "deployment", "external_integrations"} for name in (item["value"] if isinstance(item["value"], list) else [item["value"]]) if name for part in re.split(r"[,\n]", name) if part.strip()}

    for item in items:
        field, value, quote = item["field"], item["value"], item["quote"]
        if isinstance(value, list) and field in {"frontend", "backend", "ai", "external_integrations"}:
            value = list(dict.fromkeys(part.strip() for name in value for part in re.split(r"[,\n]", name) if part.strip()))
            if len(value) > 30 or (item["value"] and not value):
                raise ValueError("INVALID_PROVIDER_OUTPUT")
        if isinstance(value, list):
            value = [name for name in value if not _is_generic_field_value(field, name)]
        elif _is_generic_field_value(field, value):
            continue
        if item["value"] and value == []:
            continue
        values = value if isinstance(value, list) else [value]
        spans = _spans(document.text, quote, table_index)
        contexts = [context_at(*span) for span in spans]
        related_spans = related_value_spans(document.text, values) if not spans and value not in (None, []) else []
        # Related spans guide the reviewer to the relevant page only. They do
        # not turn an unverified AI quote into a suggested fact.
        source_spans = spans or related_spans
        evidence = [location(*source_spans[0])] if source_spans else []
        grounded = bool(spans) and value is not None
        if value == []:
            grounded = grounded and all(_NONE.search(context) for context in contexts)
        elif value is not None:
            grounded = grounded and all(v.casefold() in quote.casefold() for v in values)
        # Every matched location must agree on current-project context.
        explicit = item["context"] == "explicit" or (value == [] and item["context"] == "negated")
        decision_contexts = [context.replace("코드 검토", "코드 리뷰") if field == "features" and "코드 검토" in values else context for context in contexts]
        status = "suggested" if grounded and explicit and all(not _UNCERTAIN.search(context) and not _OTHER.search(context) for context in decision_contexts) else "unresolved"
        usages = []
        if status == "suggested" and field in {"frontend", "backend", "ai", "database", "deployment", "external_integrations"}:
            for usage in item.get("usages", []):
                name, purpose, usage_quote = usage["value"], usage["purpose"], usage["quote"]
                if name not in values or not any(name.casefold() in clause.casefold() and purpose.casefold() in clause.casefold() and not any(other != name and other.casefold() in clause.casefold() for other in technology_names) for clause in re.split(r"[;\n。]", usage_quote)):
                    continue
                usage_spans = [span for span in _spans(document.text, usage_quote, table_index) if any(start <= span[0] and span[1] <= end for start, end in spans)]
                if not usage_spans or len(usage_spans) > 30 or any(_UNCERTAIN.search(context_at(*span)) or _OTHER.search(context_at(*span)) for span in usage_spans):
                    continue
                usages.append({"value": name, "purpose": purpose, "evidence": [location(*span) for span in usage_spans]})
        # Split locations into bounded candidates; never discard the 31st source.
        for offset in range(0, max(1, len(evidence)), 30):
            chunk = evidence[offset:offset + 30]
            related_usages = [usage for usage in usages if all(any(source["start"] <= e["start"] and e["end"] <= source["end"] for source in chunk) for e in usage["evidence"])]
            identity = json.dumps([document.document_id, field, value, chunk, status, related_usages], ensure_ascii=False, sort_keys=True)
            identifier = hashlib.sha256(identity.encode()).hexdigest()[:32]
            if identifier in seen:
                continue
            seen.add(identifier)
            candidates.append({"id": identifier, "version": 1, "field": field, "proposedValue": value, "status": status, "evidence": chunk, **({"usages": _unique(related_usages)} if related_usages else {})})
    for field in profile:
        relevant = [candidate for candidate in candidates if candidate["field"] == field]
        if not relevant:
            continue
        distinct = {json.dumps(candidate["proposedValue"], ensure_ascii=False, sort_keys=True) for candidate in relevant}
        values = [candidate["proposedValue"] for candidate in relevant]
        # Multiple positively stated AI/backend/database/deployment technologies
        # are reviewable together without purposes. Preserve contradictory single
        # selections and competing frontend frameworks as unresolved candidates.
        layers = {"react": "react", "next.js": "react", "next": "react", "vue": "vue", "vue.js": "vue", "angular": "angular", "svelte": "svelte", "typescript": "language", "javascript": "language", "html": "markup", "css": "style", "tailwind css": "style", "tailwindcss": "style"}
        flat = [v for value in values if isinstance(value, list) for v in value]
        frontend_layers = {layers.get(v.casefold()) for v in flat}
        combined = list(dict.fromkeys(flat))
        evidence = _unique([e for candidate in relevant for e in candidate["evidence"]])
        usages = _unique([usage for candidate in relevant for usage in candidate.get("usages", [])])
        all_lists = all(isinstance(value, list) and value for value in values)
        scalar_values = list(dict.fromkeys(value for value in values if isinstance(value, str)))
        purpose_values = combined if all_lists else scalar_values
        separate = _separate_purposes(field, purpose_values, usages)
        selections = [candidate for candidate in relevant if any(_SELECTION.search(context_at(e['start'], e['end'])) for e in candidate['evidence'])]
        selected_values = {json.dumps(candidate['proposedValue'], ensure_ascii=False, sort_keys=True) for candidate in selections}
        selected_full_list = all_lists and any(set(candidate['proposedValue']) == set(combined) for candidate in selections)
        shared_selection_evidence = set.intersection(*[{(e['start'], e['end']) for e in candidate['evidence']} for candidate in selections]) if selections else set()
        shared_selection = any(len(_SELECTION.findall(document.text[start:end])) <= 1 for start, end in shared_selection_evidence)
        conflicting_selection = len(selected_values) > 1 and not selected_full_list and not shared_selection
        stated_coexistence = not conflicting_selection
        superset = field in {"ai", "backend"} and all_lists and any(set(value) == set(combined) for value in values)
        complementary = all_lists and (field in {"features", "external_integrations"} or ((superset or field in {"ai", "backend"}) and stated_coexistence) or separate or (field == "frontend" and None not in frontend_layers and len(frontend_layers - {"language", "markup", "style"}) <= 1))
        scalar_coexistence = field in {"deployment", "database"} and len(scalar_values) == len(distinct) and (separate or stated_coexistence)
        proposed = relevant[0]["proposedValue"] if len(distinct) == 1 else combined if all_lists else ", ".join(scalar_values)
        within_limits = len(evidence) <= 30 and len(usages) <= 30 and (len(proposed) <= profile_array_limit(field) if isinstance(proposed, list) else proposed is None or len(proposed) <= 200)
        if (len(distinct) == 1 or complementary or scalar_coexistence) and within_limits and all(candidate["status"] == "suggested" for candidate in relevant):
            identifier = hashlib.sha256(json.dumps([document.document_id, field, proposed, evidence, usages], ensure_ascii=False, sort_keys=True).encode()).hexdigest()[:32]
            merged = {"id": identifier, "version": 1, "field": field, "proposedValue": proposed, "status": "suggested", "evidence": evidence, **({"usages": usages} if usages else {})}
            first = candidates.index(relevant[0])
            candidates = [candidate for candidate in candidates if candidate not in relevant]
            candidates.insert(first, merged)
            profile[field], states[field] = proposed, "suggested"
        else:
            states[field] = "unresolved"
            for candidate in relevant:
                candidate["status"] = "unresolved"
    candidates = _split_array_candidates(candidates)
    questions = [{"id": f"review-{field}", "field": field, "message": f"{_LABELS[field]}을 직접 확인해 주세요. 미정으로 저장해도 됩니다.", "itemIds": [candidate["id"] for candidate in candidates if candidate["field"] == field], "required": False} for field in profile if states[field] != "suggested"]
    return validate_analysis_result({"schemaVersion": "1.0", "profile": profile, "fieldStates": states, "candidates": candidates, "questions": questions, "safeWarnings": ["REVIEW_CONFIRMATION_REQUIRED", "DOCUMENT_GROUNDED", "FIELDS_UNRESOLVED"], "modelMetadata": {"provider": provider, "model": model, "latencyMs": latency_ms, "promptVersion": PROMPT_VERSION, "inputFormat": input_format or ("canonical-pdf" if provider == "upstage" else "text")}})
