package gloss.common

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

// Open: every module may use any of it, sub-packages included.
@PackageInfo
@ApplicationModule(id = "common", type = ApplicationModule.Type.OPEN)
class ModuleMetadata
