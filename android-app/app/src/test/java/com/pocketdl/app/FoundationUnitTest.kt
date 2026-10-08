package com.pocketdl.app

import com.pocketdl.app.core.dispatchers.DefaultDispatcherProvider
import com.pocketdl.app.core.result.AppResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoundationUnitTest {

    @Test
    fun dispatcherProvider_providesDispatchers() {
        val provider = DefaultDispatcherProvider()
        org.junit.Assert.assertNotNull(provider.main)
        org.junit.Assert.assertNotNull(provider.io)
        org.junit.Assert.assertNotNull(provider.default)
        org.junit.Assert.assertNotNull(provider.unconfined)
    }

    @Test
    fun appResult_success_containsData() {
        val result: AppResult<String> = AppResult.Success("PocketDL Native")
        assertTrue(result is AppResult.Success)
        assertEquals("PocketDL Native", (result as AppResult.Success).data)
    }

    @Test
    fun appResult_error_containsException() {
        val exception = IllegalStateException("Test error")
        val result: AppResult<Nothing> = AppResult.Error(exception, "Test error message")
        assertTrue(result is AppResult.Error)
        assertEquals("Test error message", (result as AppResult.Error).message)
    }
}
