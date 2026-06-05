package com.stax.core.data.di

import org.koin.dsl.module

val coreDataModule = module {
    // Repository bindings added as implementations land (M0-10).
    // Use singleOf(::Impl) { bind<Interface>() } form exclusively.
}
