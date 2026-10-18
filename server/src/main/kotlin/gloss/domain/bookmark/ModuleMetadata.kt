package gloss.domain.bookmark

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "bookmark", allowedDependencies = ["common", "book :: service"])
class ModuleMetadata
