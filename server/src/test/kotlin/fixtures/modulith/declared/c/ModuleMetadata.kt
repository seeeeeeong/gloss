package fixtures.modulith.declared.c

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "c", allowedDependencies = ["a :: service"])
class ModuleMetadata
