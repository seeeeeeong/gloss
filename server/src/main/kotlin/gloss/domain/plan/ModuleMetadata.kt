package gloss.domain.plan

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    id = "plan",
    allowedDependencies = [
        "common",
        "book :: service",
        "book :: style",
        "book :: event",
        "budget :: service",
        "generation :: service",
        "generation :: job",
        "llm :: service",
        "llm :: chat",
    ],
)
class ModuleMetadata
