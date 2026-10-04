// The rules of the domain that need no I/O: password and login-throttle policies,
// key encryption, prices and reservations, TOC parsing, plan serialisation, prompt
// assembly, generated-post checks and job state transitions.
//
// Nothing here may know about Spring, JPA, an HTTP client or a database driver. That
// is kept true by none of them being on this module's classpath, which the compiler
// enforces; a convention only a reviewer enforces does not survive a busy week.
plugins {
    id("gloss.kotlin-jvm")
}
