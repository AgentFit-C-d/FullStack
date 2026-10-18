import { canPreviewComplete, canEnterDemoComplete, createCompleteDraft, editCompleteDraft, revokeCompleteAccess } from '../complete/completeModel.js'
import { createPreviewDraft, editPreviewDraft, syncPreviewSelection } from '../preview/previewModel.js'
import { canEnterDemoSelection } from '../selection/selectionModel.js'
import { limitProjectName, validateProjectName } from './projectName.js'
export { limitProjectName, validateProjectName } from './projectName.js'
import { createSelectionPreview, editSelectionPreview } from '../selection/selectionModel.js'
import { canEnterDemoRecommendation, createRecommendationPreview, editRecommendationPreview } from '../recommendation/recommendationModel.js'
import { canEnterDemoEnvironment, editEnvironment, emptyEnvironmentEntry } from '../environment/environmentModel.js'
import { canEnterDemoRole, editRole, emptyRoleEntry } from '../role/roleModel.js'
import { createDemoProfile, editDemoProfile } from '../profile/profileModel.js'
// This store contains demo data only. Server data belongs in a separate API-backed store.
export const initialDemoState = { active: false, projects: [], draftName: '', examplesVisible: false, exampleNames: {}, selectedProjectId: null, planningDrafts: {}, demoProfiles: {}, roleDrafts: {}, environmentDrafts: {}, recommendationPreviews: {}, selectionPreviews: {}, previewDrafts: {}, completeDrafts: {} }

const exampleProjects = [
  { id: 'demo-example-workout', name: '운동 기록 웹 서비스', source: 'demo' },
  { id: 'demo-example-planner', name: '팀 일정 관리 서비스', source: 'demo' },
]

export function getVisibleProjects(state) {
  return state.examplesVisible ? [...state.projects, ...exampleProjects.map(project => ({ ...project, name: state.exampleNames?.[project.id] ?? project.name }))] : state.projects
}

export function getPlanningDraft(state, id) {
  return state.planningDrafts[id] || emptyPlanningDraft
}

function updatePlanningDraft(state, id, patch) {
  if (!state.active || !getVisibleProjects(state).some(project => project.id === id)) return state
  return { ...state, planningDrafts: { ...state.planningDrafts, [id]: { ...getPlanningDraft(state, id), ...patch } } }
}

export function demoReducer(state, action) {
  switch (action.type) {
    case 'complete-enter':
    case 'complete-edit':
    case 'complete-report': {
      if (!state.active || !getVisibleProjects(state).some(project => project.id === action.id) || !canPreviewComplete(state, action.id)) return state
      const previous = state.completeDrafts?.[action.id]
      let entry
      if (action.type === 'complete-enter') {
        const signature = state.previewDrafts[action.id].selectionSignature
        if (previous && previous.source !== 'demo-ui') return state
        entry = previous ? (previous.entered && previous.selectionSignature === signature ? previous : { ...previous, entered: true, selectionSignature: signature }) : createCompleteDraft(signature)
      } else {
        if (!canEnterDemoComplete(state, action.id)) return state
        entry = editCompleteDraft(previous, action)
      }
      return entry === previous ? state : { ...state, completeDrafts: { ...state.completeDrafts, [action.id]: entry } }
    }
    case 'preview-enter':
    case 'preview-edit': {
      if (!state.active || !getVisibleProjects(state).some(project => project.id === action.id) || !canEnterDemoSelection(state, action.id)) return state
      const previous = state.previewDrafts?.[action.id]
      if (action.type === 'preview-edit' && !previous) return state
      const synced = previous ? syncPreviewSelection(previous, state.selectionPreviews[action.id]) : createPreviewDraft(state.selectionPreviews[action.id])
      const entry = action.type === 'preview-enter' ? synced : editPreviewDraft(synced, action)
      return entry === previous ? state : { ...state, previewDrafts: { ...state.previewDrafts, [action.id]: entry }, completeDrafts: !entry.visible || !entry.acknowledged ? revokeCompleteAccess(state.completeDrafts, action.id) : state.completeDrafts }
    }
    case 'selection-enter':
    case 'selection-preview': {
      if (!state.active || !getVisibleProjects(state).some(project => project.id === action.id) || !canEnterDemoRecommendation(state.demoProfiles[action.id], state.roleDrafts[action.id], state.environmentDrafts[action.id])) return state
      const previous = state.selectionPreviews[action.id]
      if (action.type === 'selection-enter') return previous ? state : { ...state, selectionPreviews: { ...state.selectionPreviews, [action.id]: createSelectionPreview() } }
      if (!previous) return state
      const entry = editSelectionPreview(previous, action)
      if (entry === previous) return state
      const preview = state.previewDrafts?.[action.id] ? syncPreviewSelection(state.previewDrafts[action.id], entry) : null
      return { ...state, selectionPreviews: { ...state.selectionPreviews, [action.id]: entry },
        previewDrafts: preview ? { ...state.previewDrafts, [action.id]: preview } : state.previewDrafts,
        completeDrafts: preview && (!preview.visible || !preview.acknowledged) ? revokeCompleteAccess(state.completeDrafts, action.id) : state.completeDrafts,
      }
    }
    case 'recommendation-preview': {
      if (!state.active || !getVisibleProjects(state).some(project => project.id === action.id) || !canEnterDemoRecommendation(state.demoProfiles[action.id], state.roleDrafts[action.id], state.environmentDrafts[action.id])) return state
      const previous = state.recommendationPreviews[action.id] || createRecommendationPreview()
      const entry = editRecommendationPreview(previous, action)
      return entry === previous ? state : { ...state, recommendationPreviews: { ...state.recommendationPreviews, [action.id]: entry } }
    }
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
        return { ...state, demoProfiles: { ...state.demoProfiles, [action.id]: createDemoProfile(getVisibleProjects(state).find(project => project.id === action.id).name, true) } }
      }
      if (action.type === 'profile-preview' && existing) return state
      if (action.type === 'profile-edit' && !existing) return state
      const entry = action.type === 'profile-preview' ? createDemoProfile(getVisibleProjects(state).find(project => project.id === action.id).name) : editDemoProfile(existing, action)
      const confirmedName = action.type === 'profile-edit' && action.edit === 'confirm' && entry !== existing && entry.confirmed ? entry.profile.project_name : null
      return { ...state,
        projects: confirmedName === null ? state.projects : state.projects.map(project => project.id === action.id ? { ...project, name: confirmedName } : project),
        exampleNames: confirmedName !== null && exampleProjects.some(project => project.id === action.id) ? { ...state.exampleNames, [action.id]: confirmedName } : state.exampleNames,
        demoProfiles: { ...state.demoProfiles, [action.id]: entry },
      }
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
