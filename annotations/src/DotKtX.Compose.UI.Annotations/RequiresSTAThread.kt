package dotktx.compose.ui.annotations

/**
 * The annotated function needs the main thread to be a single-threaded apartment (STA), as WinUI does.
 * The analyzers warn when a top-level `main` that calls it is not marked `@STAThreadAttribute`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
@MustBeDocumented
annotation class RequiresSTAThread
