package fixtures.modulith.cycle.b

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "b", allowedDependencies = ["a"])
class ModuleMetadata
