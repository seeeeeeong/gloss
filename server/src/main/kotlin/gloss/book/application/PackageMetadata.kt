package gloss.book.application

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

// A book's contents are what other modules write from: "book :: application".
@PackageInfo
@NamedInterface("application")
class PackageMetadata
