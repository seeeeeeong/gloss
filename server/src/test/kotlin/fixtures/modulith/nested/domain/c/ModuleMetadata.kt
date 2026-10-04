package fixtures.modulith.nested.domain.c

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "c", allowedDependencies = ["a :: service"])
class ModuleMetadata
