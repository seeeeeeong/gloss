package gloss.domain.llm

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "llm", allowedDependencies = ["common", "apikey :: service", "budget :: service"])
class ModuleMetadata
