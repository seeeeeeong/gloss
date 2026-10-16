package gloss.domain.apikey

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "apikey", allowedDependencies = ["common", "account :: service", "account :: event"])
class ModuleMetadata
