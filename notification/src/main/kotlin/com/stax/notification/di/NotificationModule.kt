package com.stax.notification.di

import com.stax.notification.alarm.AndroidExactAlarmPermission
import com.stax.notification.alarm.ExactAlarmPermission
import com.stax.notification.alarm.ExactAlarmPermissionMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val notificationModule = module {
    single<ExactAlarmPermission> { AndroidExactAlarmPermission(androidContext()) }
    singleOf(::ExactAlarmPermissionMonitor)
}
