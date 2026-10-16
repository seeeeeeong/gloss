package gloss.domain.audit

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "audit", allowedDependencies = ["common", "account :: event", "apikey :: event"])
class ModuleMetadata
