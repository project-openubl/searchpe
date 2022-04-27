package io.github.project.openubl.searchpe.mapper;

import io.github.project.openubl.searchpe.dto.BasicUserDto;
import io.github.project.openubl.searchpe.models.jpa.entity.BasicUserEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "cdi")
public interface BasicUserMapper {

    @Mapping(source = "permissions", target = "permissions")
    @Mapping(source = "password", target = "password", ignore = true)
    BasicUserDto toDto(BasicUserEntity entity);

    default Set<String> permissions(String permissions) {
        return Arrays.stream(permissions.split(","))
                .sorted()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

}
