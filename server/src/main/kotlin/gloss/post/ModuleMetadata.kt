package gloss.post

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(
    id = "post",
    allowedDependencies = [
        "common",
        "book :: application",
        "budget :: application",
        "generation :: application",
        "generation :: job",
        "llm :: application",
        "llm :: chat",
        "plan :: application",
        "plan :: event",
    ],
)
class ModuleMetadata
