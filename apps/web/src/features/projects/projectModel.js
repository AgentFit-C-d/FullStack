import { canEnterDemoEnvironment, editEnvironment, emptyEnvironmentEntry } from '../environment/environmentModel.js'
import { canEnterDemoRole, editRole, emptyRoleEntry } from '../role/roleModel.js'
import { createDemoProfile, editDemoProfile } from '../profile/profileModel.js'
// This store contains demo data only. Server data belongs in a separate API-backed store.
export const initialDemoState = { active: false, projects: [], draftName: '', examplesVisible: false, selectedProjectId: null, planningDrafts: {}, demoProfiles: {}, roleDrafts: {}, environmentDrafts: {} }

const exampleProjects = [
  { id: 'demo-example-workout', name: '운동 기록 웹 서비스', source: 'demo' },
  { id: 'demo-example-planner', name: '팀 일정 관리 서비스', source: 'demo' },
]

export function getVisibleProjects(state) {
  return state.examplesVisible ? [...state.projects, ...exampleProjects] : state.projects
}

export function getPlanningDraft(state, id) {
  return state.planningDrafts[id] || emptyPlanningDraft
}

function updatePlanningDraft(state, id, patch) {
  if (!state.active || !getVisibleProjects(state).some(project => project.id === id)) return state
  return { ...state, planningDrafts: { ...state.planningDrafts, [id]: { ...getPlanningDraft(state, id), ...patch } } }
}

export const limitProjectName = (value) => Array.from(value).slice(0, 200).join('')

export function validateProjectName(value) {
  if (!value.trim()) return '프로젝트 이름을 입력해주세요.'
  if (Array.from(value).length > 200) return '프로젝트 이름은 최대 200자까지 입력할 수 있어요.'
  return ''
}

export function demoReducer(state, action) {
  switch (action.type) {
    case 'environment-edit':
    case 'environment-confirm': {
      if (!state.active || !getVisibleProjects(state).some(project => project.id === action.id) || !canEnterDemoEnvironment(state.demoProfiles[action.id], state.roleDrafts[action.id])) return state
      const previous = state.environmentDrafts[action.id] || emptyEnvironmentEntry
      const entry = editEnvironment(previous, action)
      return entry === previous ? state : { ...state, environmentDrafts: { ...state.environmentDrafts, [action.id]: entry } }
    }
    case 'role-edit':
    case 'role-confirm': {
      if (!state.active || !getVisibleProjects(state).some(project => project.id === action.id) || !canEnterDemoRole(state.demoProfiles[action.id])) return state
      const previous = state.roleDrafts[action.id] || emptyRoleEntry
      const entry = editRole(previous, action)
      return entry === previous ? state : { ...state, roleDrafts: { ...state.roleDrafts, [action.id]: entry } }
    }
    case 'profile-load-example':
    case 'profile-preview':
    case 'profile-edit': {
      if (!state.active || !getVisibleProjects(state).some(project => project.id === action.id)) return state
      const existing = state.demoProfiles[action.id]
      if (action.type === 'profile-load-example') {
        if (!existing || action.confirmed !== true) return state
        return { ...state, demoProfiles: { ...state.demoProfiles, [action.id]: createDemoProfile('', true) } }
      }
      if (action.type === 'profile-preview' && existing) return state
      if (action.type === 'profile-edit' && !existing) return state
      const entry = action.type === 'profile-preview' ? createDemoProfile(getVisibleProjects(state).find(project => project.id === action.id).name) : editDemoProfile(existing, action)
      return { ...state, demoProfiles: { ...state.demoProfiles, [action.id]: entry } }
    }
    case 'planning-tab':
      return ['file', 'text'].includes(action.tab) ? updatePlanningDraft(state, action.id, { tab: action.tab }) : state
    case 'planning-file':
      return validatePlanningFiles(action.file ? [action.file] : []) ? state : updatePlanningDraft(state, action.id, { file: action.file })
    case 'planning-text':
      return updatePlanningDraft(state, action.id, { text: limitPlanningText(action.text) })
    case 'enter':
      return { ...initialDemoState, active: true }
    case 'exit':
      return initialDemoState
    case 'cancel':
      return { ...state, draftName: '' }
    case 'draft':
      return { ...state, draftName: limitProjectName(action.value) }
    case 'create':
      if (!state.active || validateProjectName(action.name)) return state
      return {
        ...state,
        draftName: '',
        selectedProjectId: action.id,
        projects: [{ id: action.id, name: action.name.trim(), source: 'demo' }, ...state.projects],
      }
    case 'select':
      return state.active && getVisibleProjects(state).some(project => project.id === action.id)
        ? { ...state, selectedProjectId: action.id }
        : state
    case 'examples': {
      if (!state.active) return state
      const examplesVisible = !state.examplesVisible
      const selectedExample = exampleProjects.some(project => project.id === state.selectedProjectId)
      return { ...state, examplesVisible, selectedProjectId: !examplesVisible && selectedExample ? null : state.selectedProjectId }
    }
    default:
      return state
  }
}
import { emptyPlanningDraft, limitPlanningText, validatePlanningFiles } from '../planning/planningModel.js'
