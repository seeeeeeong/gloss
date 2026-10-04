package gloss.account

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(allowedDependencies = ["common"])
class ModuleMetadata
