package com.property.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.dto.OwnerDTO;
import com.property.entity.Owner;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface OwnerService {
    Page<Owner> getPage(int page, int size, String name, String buildingNo, String unitNo, String roomNo, String status);
    Owner getById(Long id);
    void create(OwnerDTO dto);
    void update(Long id, OwnerDTO dto);
    void delete(Long id);
    Map<String, Object> importFromExcel(MultipartFile file);
    List<Owner> getAll();
}
