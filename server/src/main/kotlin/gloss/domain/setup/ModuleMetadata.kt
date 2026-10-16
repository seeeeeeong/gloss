package gloss.domain.setup

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "setup", allowedDependencies = ["common", "book :: service"])
class ModuleMetadata
