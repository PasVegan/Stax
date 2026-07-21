package com.stax.detekt

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * The simple name being invoked, or `null` when the callee is not a plain name reference (a call on
 * a lambda variable, an invoke on a receiver expression, …).
 *
 * Type arguments live on the call expression rather than the callee, so `tween<Float>(...)` still
 * resolves to `"tween"`. A fully-qualified call such as `androidx.compose.animation.core.tween(...)`
 * is a [org.jetbrains.kotlin.psi.KtDotQualifiedExpression] wrapping this call, and the callee inside
 * it is still the bare name — so these rules match it too.
 */
internal fun KtCallExpression.calleeName(): String? =
    (calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
