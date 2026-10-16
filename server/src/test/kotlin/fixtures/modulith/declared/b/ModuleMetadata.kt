package fixtures.modulith.declared.b

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "b", allowedDependencies = ["a :: service"])
class ModuleMetadata
