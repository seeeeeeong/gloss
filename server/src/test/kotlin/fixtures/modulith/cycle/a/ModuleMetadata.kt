package fixtures.modulith.cycle.a

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "a", allowedDependencies = ["b"])
class ModuleMetadata
