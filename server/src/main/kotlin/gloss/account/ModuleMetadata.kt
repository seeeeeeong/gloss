package gloss.account

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "account", allowedDependencies = ["common"])
class ModuleMetadata
