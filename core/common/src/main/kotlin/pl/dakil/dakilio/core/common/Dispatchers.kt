package pl.dakil.dakilio.core.common

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: DakilioDispatchers)

enum class DakilioDispatchers { Default, IO }

/** A scope that lives as long as the process, for work that must outlive a screen. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
