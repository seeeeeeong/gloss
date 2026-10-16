package gloss.domain.budget

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "budget", allowedDependencies = ["common"])
class ModuleMetadata
