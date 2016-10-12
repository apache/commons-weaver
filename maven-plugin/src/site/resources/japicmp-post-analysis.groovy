def it = jApiClasses.iterator()
while (it.hasNext()) {
  def jApiClass = it.next()
  // look for false positive on introduced superclass level
  def jApiSuperclass = jApiClass.getSuperclass()
  def newSuper = jApiSuperclass.getNewSuperclassName()
  if (newSuper.isPresent() && newSuper.get().endsWith(".AbstractCWMojo")) {
    jApiSuperclass.getCompatibilityChanges().clear();
  }
}
return jApiClasses
