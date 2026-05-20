package com.loopers.domain.user.vo

import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

/**
 * 
 * @since   2026. 5. 18.
 * @author  hyunjikoh
 */
@Embeddable
data class Name(
    @Column(name = "name", nullable = false)
    val value: String,
){
    init{
        if(value.isBlank()){
            throw CoreException(ErrorType.BAD_REQUEST, "name can't be empty")
        }
    }

    fun masked():String{
        if(value.length <=1) return "*"
        return value.dropLast(1) + "*"
    }
}
