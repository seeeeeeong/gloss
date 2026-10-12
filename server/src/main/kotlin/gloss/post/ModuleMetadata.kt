package gloss.post

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "post", allowedDependencies = ["common"])
class ModuleMetadata
