export function inputsDirty(saved, form) {
  if (!saved?.developer || !saved?.environment) return false
  const developer = saved.developer
  const environment = saved.environment
  const responsibilities = form.responsibilities.split('\n').map(line => line.trim()).filter(Boolean)
  return developer.role !== form.role.trim()
    || JSON.stringify(developer.responsibilities || []) !== JSON.stringify(responsibilities)
    || environment.osFamily !== form.osFamily.trim().toUpperCase()
    || environment.clientId !== form.clientId.trim()
    || environment.clientVersion !== form.clientVersion.trim()
    || environment.installedComponentsKnown !== form.installedComponentsKnown
}
