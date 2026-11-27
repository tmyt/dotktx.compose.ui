package dotktx.compose.ui.analyzers

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirExpressionChecker
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirLoop
import org.jetbrains.kotlin.fir.expressions.FirLoopJump
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirThrowExpression
import org.jetbrains.kotlin.fir.expressions.resolvedArgumentMapping
import org.jetbrains.kotlin.fir.expressions.unwrapArgument
import org.jetbrains.kotlin.fir.references.toResolvedNamedFunctionSymbol
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitorVoid
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name

private val AT_MOST_ONE_ELEMENT_CONTENT = ClassId(ANNOTATIONS, Name.identifier("AtMostOneElementContent"))
private val EMITS_ONE_ELEMENT = ClassId(ANNOTATIONS, Name.identifier("EmitsOneElement"))

/**
 * A lambda passed to a parameter marked `@AtMostOneElementContent` that is certain to emit two elements.
 *
 * Only what the lambda itself shows is counted: its top-level statements that call a function marked
 * `@EmitsOneElement`. Anything else (conditions, loops, calls to unmarked functions, nested lambdas)
 * counts as zero, so the checker never reports content that could be valid. Counting stops at a
 * statement that can leave the lambda early, because the statements after it may not run.
 */
internal object SingleElementContentChecker : FirExpressionChecker<FirFunctionCall>(MppCheckerKind.Common) {
    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val callee = expression.calleeReference.toResolvedNamedFunctionSymbol() ?: return
        val mapping = expression.resolvedArgumentMapping ?: return
        for ((argument, parameter) in mapping) {
            if (!parameter.symbol.hasAnnotation(AT_MOST_ONE_ELEMENT_CONTENT, context.session)) continue
            val lambda = (argument.unwrapArgument() as? FirAnonymousFunctionExpression)?.anonymousFunction ?: continue
            val second = secondElement(lambda, context.session) ?: continue
            reporter.reportOn(
                second.calleeReference.source,
                AnalyzerDiagnostics.SEVERAL_ELEMENTS_IN_SINGLE_ELEMENT_CONTENT,
                "${callee.name} ${parameter.name}",
            )
        }
    }

    /** The call that emits the second element when [lambda] is certain to emit two, or null. */
    private fun secondElement(lambda: FirAnonymousFunction, session: FirSession): FirFunctionCall? {
        var count = 0
        for (statement in lambda.body?.statements.orEmpty()) {
            // The last expression of a lambda is wrapped in a return to the lambda; nothing runs after one.
            val returned = (statement as? FirReturnExpression)?.takeIf { it.target.labeledElement === lambda }
            val call = (returned?.result ?: statement) as? FirFunctionCall
            if (call != null && call.calleeReference.toResolvedNamedFunctionSymbol()?.hasAnnotation(EMITS_ONE_ELEMENT, session) == true) {
                if (++count == 2) return call
            }
            if (returned != null || canLeaveEarly(returned?.result ?: statement)) return null
        }
        return null
    }

    /**
     * Whether [statement] contains a jump out of itself: a return or a break/continue whose target
     * is outside the statement, or a throw.
     */
    private fun canLeaveEarly(statement: FirStatement): Boolean {
        val inside = HashSet<FirElement>()
        var leaves = false
        statement.accept(object : FirDefaultVisitorVoid() {
            override fun visitElement(element: FirElement) {
                if (leaves) return
                if (element is FirAnonymousFunction || element is FirLoop) inside.add(element)
                when (element) {
                    is FirReturnExpression -> if (element.target.labeledElement !in inside) leaves = true
                    is FirLoopJump -> if (element.target.labeledElement !in inside) leaves = true
                    is FirThrowExpression -> leaves = true
                    else -> {}
                }
                element.acceptChildren(this)
            }
        })
        return leaves
    }
}
