package dotktx.compose.ui.winui.analyzers

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.references.toResolvedNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirFileSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class WinUICheckers(session: FirSession) : FirAdditionalCheckersExtension(session) {
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirExpressionChecker<FirFunctionCall>> = setOf(ApplicationCallChecker)
    }
}

private val WINUI_PACKAGE = FqName("dotktx.compose.ui.winui")
private val APPLICATION = CallableId(WINUI_PACKAGE, Name.identifier("application"))
private val STA_THREAD = ClassId(FqName("System"), Name.identifier("STAThreadAttribute"))
private val MAIN = Name.identifier("main")

/** `application { }` called from a top-level `main` that is not marked `@STAThreadAttribute`. */
internal object ApplicationCallChecker : FirExpressionChecker<FirFunctionCall>(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val callee = expression.calleeReference.toResolvedNamedFunctionSymbol() ?: return
        if (callee.callableId != APPLICATION) return
        // The list starts with the file; the next entry is the top-level declaration around the call.
        val main = context.containingDeclarations.firstOrNull { it !is FirFileSymbol } as? FirNamedFunctionSymbol ?: return
        if (main.name != MAIN || main.callableId.classId != null) return
        if (main.hasAnnotation(STA_THREAD, context.session)) return
        reporter.reportOn(expression.calleeReference.source, WinUIDiagnostics.MAIN_WITHOUT_STA_THREAD)
    }
}
