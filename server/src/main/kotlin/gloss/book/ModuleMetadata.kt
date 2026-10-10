package gloss.book

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "book", allowedDependencies = ["common"])
class ModuleMetadata
