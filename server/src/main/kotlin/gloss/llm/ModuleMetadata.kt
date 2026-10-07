package gloss.llm

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(id = "llm", allowedDependencies = ["common", "apikey :: application", "budget :: application"])
class ModuleMetadata
