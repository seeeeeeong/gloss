package gloss.domain.post

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    id = "post",
    allowedDependencies = [
        "common",
        "book :: service",
        "budget :: service",
        "generation :: service",
        "generation :: job",
        "llm :: service",
        "llm :: chat",
        "plan :: service",
        "plan :: event",
    ],
)
class ModuleMetadata
