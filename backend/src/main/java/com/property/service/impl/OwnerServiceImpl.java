package com.property.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.property.common.BusinessException;
import com.property.common.ErrorCode;
import com.property.config.AccountSetContext;
import com.property.dto.OwnerDTO;
import com.property.entity.Owner;
import com.property.mapper.OwnerMapper;
import com.property.service.OwnerService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OwnerServiceImpl implements OwnerService {
    
    private final OwnerMapper ownerMapper;
    
    @Override
    public Page<Owner> getPage(int page, int size, String name, String buildingNo, 
                               String unitNo, String roomNo, String status) {
        Page<Owner> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Owner> wrapper = new LambdaQueryWrapper<>();
        
        wrapper.eq(Owner::getAccountSetId, AccountSetContext.getCurrentAccountSetId());
        
        if (StringUtils.hasText(name)) {
            wrapper.like(Owner::getName, name);
        }
        if (StringUtils.hasText(buildingNo)) {
            wrapper.eq(Owner::getBuildingNo, buildingNo);
        }
        if (StringUtils.hasText(unitNo)) {
            wrapper.eq(Owner::getUnitNo, unitNo);
        }
        if (StringUtils.hasText(roomNo)) {
            wrapper.eq(Owner::getRoomNo, roomNo);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(Owner::getStatus, status);
        }
        wrapper.orderByAsc(Owner::getId);
        
        return ownerMapper.selectPage(pageParam, wrapper);
    }
    
    @Override
    public Owner getById(Long id) {
        Owner owner = ownerMapper.selectById(id);
        if (owner == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "业主不存在");
        }
        return owner;
    }
    
    @Override
    @Transactional
    public void create(OwnerDTO dto) {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        
        LambdaQueryWrapper<Owner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Owner::getAccountSetId, accountSetId)
               .eq(Owner::getBuildingNo, dto.getBuildingNo())
               .eq(Owner::getUnitNo, dto.getUnitNo())
               .eq(Owner::getRoomNo, dto.getRoomNo());
        
        if (ownerMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.DATA_DUPLICATE, "该房间已存在业主信息");
        }
        
        Owner owner = new Owner();
        owner.setId(null);
        owner.setAccountSetId(accountSetId);
        owner.setName(dto.getName());
        owner.setBuildingNo(dto.getBuildingNo());
        owner.setUnitNo(dto.getUnitNo());
        owner.setRoomNo(dto.getRoomNo());
        owner.setPhone(dto.getPhone());
        owner.setArea(dto.getArea());
        owner.setMoveInDate(dto.getMoveInDate());
        owner.setStatus(dto.getStatus() != null ? dto.getStatus() : "VACANT");
        
        ownerMapper.insert(owner);
    }
    
    @Override
    @Transactional
    public void update(Long id, OwnerDTO dto) {
        Owner owner = getById(id);
        
        owner.setName(dto.getName());
        owner.setPhone(dto.getPhone());
        owner.setArea(dto.getArea());
        owner.setMoveInDate(dto.getMoveInDate());
        owner.setStatus(dto.getStatus());
        
        ownerMapper.updateById(owner);
    }
    
    @Override
    @Transactional
    public void delete(Long id) {
        getById(id);
        ownerMapper.deleteById(id);
    }
    
    @Override
    @Transactional
    public Map<String, Object> importFromExcel(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                try {
                    Owner owner = parseOwnerFromRow(row, accountSetId);
                    
                    LambdaQueryWrapper<Owner> wrapper = new LambdaQueryWrapper<>();
                    wrapper.eq(Owner::getAccountSetId, accountSetId)
                           .eq(Owner::getBuildingNo, owner.getBuildingNo())
                           .eq(Owner::getUnitNo, owner.getUnitNo())
                           .eq(Owner::getRoomNo, owner.getRoomNo());
                    
                    Owner existing = ownerMapper.selectOne(wrapper);
                    if (existing != null) {
                        existing.setName(owner.getName());
                        existing.setPhone(owner.getPhone());
                        existing.setArea(owner.getArea());
                        existing.setMoveInDate(owner.getMoveInDate());
                        existing.setStatus(owner.getStatus());
                        ownerMapper.updateById(existing);
                    } else {
                        ownerMapper.insert(owner);
                    }
                    successCount++;
                } catch (Exception e) {
                    errors.add("第" + (i + 1) + "行: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.EXCEL_PARSE_ERROR, "Excel解析失败: " + e.getMessage());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("errors", errors);
        return result;
    }
    
    private Owner parseOwnerFromRow(Row row, Long accountSetId) {
        Owner owner = new Owner();
        owner.setAccountSetId(accountSetId);
        owner.setName(getCellStringValue(row.getCell(0)));
        owner.setBuildingNo(getCellStringValue(row.getCell(1)));
        owner.setUnitNo(getCellStringValue(row.getCell(2)));
        owner.setRoomNo(getCellStringValue(row.getCell(3)));
        owner.setPhone(getCellStringValue(row.getCell(4)));
        
        Cell areaCell = row.getCell(5);
        if (areaCell != null && areaCell.getCellType() == CellType.NUMERIC) {
            owner.setArea(BigDecimal.valueOf(areaCell.getNumericCellValue()));
        }
        
        Cell dateCell = row.getCell(6);
        if (dateCell != null && dateCell.getCellType() == CellType.NUMERIC) {
            Date date = dateCell.getDateCellValue();
            owner.setMoveInDate(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate());
        }
        
        String status = getCellStringValue(row.getCell(7));
        owner.setStatus(StringUtils.hasText(status) ? status : "VACANT");
        
        return owner;
    }
    
    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            default -> "";
        };
    }
    
    @Override
    public List<Owner> getAll() {
        LambdaQueryWrapper<Owner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Owner::getAccountSetId, AccountSetContext.getCurrentAccountSetId());
        return ownerMapper.selectList(wrapper);
    }
}
