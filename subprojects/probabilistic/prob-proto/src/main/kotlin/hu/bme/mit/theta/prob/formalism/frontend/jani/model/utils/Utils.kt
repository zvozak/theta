package hu.bme.mit.theta.interchange.jani.model.utils

fun String.upperSnakeToLowerKebabCase(): String = toLowerCase().replace("_", "-")
