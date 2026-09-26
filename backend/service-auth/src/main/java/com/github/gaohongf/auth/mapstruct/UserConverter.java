package com.github.gaohongf.auth.mapstruct;

import org.mapstruct.Mapper;

import com.github.gaohongf.auth.entity.po.UserEntity;
import com.github.gaohongf.auth.res.UserRes;

@Mapper(componentModel = "spring")
public interface UserConverter {
    UserRes toRes(UserEntity user);
}
