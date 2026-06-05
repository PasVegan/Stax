package com.stax.core.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import org.junit.jupiter.api.Test

private enum class MyError : Error { X, Y }

class ResultTest {

    // map

    @Test
    fun `map transforms Success data`() {
        val result = Result.Success(1).map { it + 1 }
        assertThat(result).isEqualTo(Result.Success(2))
    }

    @Test
    fun `map on Error returns same Error`() {
        val result: Result<Int, MyError> = Result.Error(MyError.X)
        val mapped = result.map { it + 1 }
        assertThat(mapped).isEqualTo(Result.Error(MyError.X))
    }

    // onSuccess

    @Test
    fun `onSuccess invokes action and returns same Success`() {
        var called = false
        val result = Result.Success(42).onSuccess { called = true }
        assertThat(called).isEqualTo(true)
        assertThat(result).isEqualTo(Result.Success(42))
    }

    @Test
    fun `onSuccess on Error does not invoke action and returns same Error`() {
        var called = false
        val result: Result<Int, MyError> = Result.Error(MyError.X)
        val returned = result.onSuccess { called = true }
        assertThat(called).isEqualTo(false)
        assertThat(returned).isEqualTo(Result.Error(MyError.X))
    }

    // onFailure

    @Test
    fun `onFailure invokes action and returns same Error`() {
        var captured: MyError? = null
        val result: Result<Int, MyError> = Result.Error(MyError.X)
        val returned = result.onFailure { captured = it }
        assertThat(captured).isEqualTo(MyError.X)
        assertThat(returned).isEqualTo(Result.Error(MyError.X))
    }

    @Test
    fun `onFailure on Success does not invoke action and returns same Success`() {
        var called = false
        val result: Result<Int, MyError> = Result.Success(1)
        val returned = result.onFailure { called = true }
        assertThat(called).isEqualTo(false)
        assertThat(returned).isEqualTo(Result.Success(1))
    }

    // asEmptyResult

    @Test
    fun `asEmptyResult on Success returns Success of Unit`() {
        val result: EmptyResult<MyError> = Result.Success(99).asEmptyResult()
        assertThat(result).isEqualTo(Result.Success(Unit))
    }

    @Test
    fun `asEmptyResult on Error returns same Error`() {
        val result: Result<Int, MyError> = Result.Error(MyError.X)
        val empty: EmptyResult<MyError> = result.asEmptyResult()
        assertThat(empty).isEqualTo(Result.Error(MyError.X))
    }

    // acceptance criteria chains

    @Test
    fun `Success onSuccess then map chains and yields Success`() {
        val result = Result.Success(1)
            .onSuccess { }
            .map { it + 1 }
        assertThat(result).isEqualTo(Result.Success(2))
    }

    @Test
    fun `Error onFailure then asEmptyResult returns Error`() {
        val result: Result<Int, MyError> = Result.Error(MyError.X)
        val empty = result.onFailure { }.asEmptyResult()
        assertThat(empty).isEqualTo(Result.Error(MyError.X))
    }

    // type checks

    @Test
    fun `Success is instance of Result`() {
        assertThat(Result.Success(1)).isInstanceOf(Result.Success::class)
    }

    @Test
    fun `Error is instance of Result`() {
        val r: Result<Nothing, MyError> = Result.Error(MyError.Y)
        assertThat(r).isInstanceOf(Result.Error::class)
    }
}
