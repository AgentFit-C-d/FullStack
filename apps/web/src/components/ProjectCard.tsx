import type { FormEvent } from 'react';
import type { Project } from '../api/types';

interface ProjectCardProps {
  projects: Project[];
  selectedId: string;
  newName: string;
  busy: boolean;
  onSelect: (projectId: string) => void;
  onNameChange: (name: string) => void;
  onCreate: (event: FormEvent<HTMLFormElement>) => void;
}

export function ProjectCard({
  projects,
  selectedId,
  newName,
  busy,
  onSelect,
  onNameChange,
  onCreate,
}: ProjectCardProps) {
  const selectedProject = projects.find((project) => project.id === selectedId);

  return (
    <article className="card">
      <div className="card-title">
        <span className="step">01</span>
        <h2>프로젝트</h2>
      </div>
      <select value={selectedId} onChange={(event) => onSelect(event.target.value)}>
        <option value="">프로젝트를 선택하세요</option>
        {projects.map((project) => (
          <option key={project.id} value={project.id}>
            {project.name}
          </option>
        ))}
      </select>
      <form className="inline-form" onSubmit={onCreate}>
        <input
          value={newName}
          onChange={(event) => onNameChange(event.target.value)}
          placeholder="새 프로젝트 이름"
        />
        <button disabled={busy}>생성</button>
      </form>
      {selectedProject && (
        <p className="meta">
          현재 버전 v{selectedProject.version} · 입력 revision{' '}
          {selectedProject.currentInputRevision ?? '없음'}
        </p>
      )}
    </article>
  );
}
