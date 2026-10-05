package gloss.apikey

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "apikey", allowedDependencies = ["common", "account :: application", "account :: event"])
class ModuleMetadata
