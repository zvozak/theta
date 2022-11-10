package hu.bme.mit.theta.probabilistic

fun Double.equals(other: Double, tolerance: Double): Boolean {
    val d = this - other
    return d < tolerance && d > -tolerance
}