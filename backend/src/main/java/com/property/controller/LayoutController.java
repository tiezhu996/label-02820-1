package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.aspect.OperationLog;
import com.property.common.Result;
import com.property.config.AccountSetContext;
import com.property.entity.Building;
import com.property.entity.Owner;
import com.property.entity.Parking;
import com.property.mapper.BuildingMapper;
import com.property.mapper.OwnerMapper;
import com.property.mapper.ParkingMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/layout")
@RequiredArgsConstructor
public class LayoutController {
    
    private final BuildingMapper buildingMapper;
    private final OwnerMapper ownerMapper;
    private final ParkingMapper parkingMapper;
    
    // ========== 楼栋布局 ==========
    
    @GetMapping("/buildings")
    public Result<List<BuildingLayoutData>> getBuildingLayout() {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        
        LambdaQueryWrapper<Building> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Building::getAccountSetId, accountSetId)
               .orderByAsc(Building::getBuildingNo);
        List<Building> buildings = buildingMapper.selectList(wrapper);
        
        // 获取所有业主信息
        LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(Owner::getAccountSetId, accountSetId);
        List<Owner> owners = ownerMapper.selectList(ownerWrapper);
        
        // 按楼栋分组
        Map<String, List<Owner>> ownersByBuilding = owners.stream()
                .collect(Collectors.groupingBy(Owner::getBuildingNo));
        
        List<BuildingLayoutData> result = new ArrayList<>();
        for (Building building : buildings) {
            BuildingLayoutData data = new BuildingLayoutData();
            data.setId(building.getId());
            data.setBuildingNo(building.getBuildingNo());
            data.setUnitCount(building.getUnitCount());
            data.setFloorCount(building.getFloorCount());
            data.setRoomsPerFloor(building.getRoomsPerFloor());
            data.setPositionX(building.getPositionX());
            data.setPositionY(building.getPositionY());
            
            List<Owner> buildingOwners = ownersByBuilding.getOrDefault(building.getBuildingNo(), new ArrayList<>());
            data.setTotalRooms(building.getUnitCount() * building.getFloorCount() * building.getRoomsPerFloor());
            data.setOccupiedRooms((int) buildingOwners.stream().filter(o -> "OCCUPIED".equals(o.getStatus())).count());
            data.setOwners(buildingOwners);
            
            result.add(data);
        }
        
        return Result.success(result);
    }
    
    @PostMapping("/buildings")
    @OperationLog(operation = "创建楼栋")
    public Result<Void> createBuilding(@Valid @RequestBody BuildingDTO dto) {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        
        // 检查楼栋号是否重复
        LambdaQueryWrapper<Building> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Building::getAccountSetId, accountSetId)
               .eq(Building::getBuildingNo, dto.getBuildingNo());
        if (buildingMapper.selectCount(wrapper) > 0) {
            return Result.fail("楼栋号已存在");
        }
        
        Building building = new Building();
        building.setAccountSetId(accountSetId);
        building.setBuildingNo(dto.getBuildingNo());
        building.setUnitCount(dto.getUnitCount() != null ? dto.getUnitCount() : 1);
        building.setFloorCount(dto.getFloorCount() != null ? dto.getFloorCount() : 1);
        building.setRoomsPerFloor(dto.getRoomsPerFloor() != null ? dto.getRoomsPerFloor() : 2);
        building.setPositionX(dto.getPositionX() != null ? dto.getPositionX() : 0);
        building.setPositionY(dto.getPositionY() != null ? dto.getPositionY() : 0);
        
        buildingMapper.insert(building);
        return Result.success();
    }
    
    @PutMapping("/buildings/{id}")
    @OperationLog(operation = "更新楼栋")
    public Result<Void> updateBuilding(@PathVariable Long id, @Valid @RequestBody BuildingDTO dto) {
        Building building = buildingMapper.selectById(id);
        if (building != null) {
            building.setUnitCount(dto.getUnitCount());
            building.setFloorCount(dto.getFloorCount());
            building.setRoomsPerFloor(dto.getRoomsPerFloor());
            building.setPositionX(dto.getPositionX());
            building.setPositionY(dto.getPositionY());
            buildingMapper.updateById(building);
        }
        return Result.success();
    }
    
    @DeleteMapping("/buildings/{id}")
    @OperationLog(operation = "删除楼栋")
    public Result<Void> deleteBuilding(@PathVariable Long id) {
        buildingMapper.deleteById(id);
        return Result.success();
    }
    
    // ========== 车位布局 ==========
    
    @GetMapping("/parkings")
    public Result<List<ParkingLayoutData>> getParkingLayout() {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        
        LambdaQueryWrapper<Parking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Parking::getAccountSetId, accountSetId)
               .orderByAsc(Parking::getParkingNo);
        List<Parking> parkings = parkingMapper.selectList(wrapper);
        
        // 获取业主信息
        Set<Long> ownerIds = parkings.stream()
                .filter(p -> p.getOwnerId() != null)
                .map(Parking::getOwnerId)
                .collect(Collectors.toSet());
        
        Map<Long, Owner> ownerMap = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            List<Owner> owners = ownerMapper.selectBatchIds(ownerIds);
            ownerMap = owners.stream().collect(Collectors.toMap(Owner::getId, o -> o));
        }
        
        List<ParkingLayoutData> result = new ArrayList<>();
        for (Parking parking : parkings) {
            ParkingLayoutData data = new ParkingLayoutData();
            data.setId(parking.getId());
            data.setParkingNo(parking.getParkingNo());
            data.setStatus(parking.getStatus());
            data.setOwnerId(parking.getOwnerId());
            
            if (parking.getOwnerId() != null && ownerMap.containsKey(parking.getOwnerId())) {
                Owner owner = ownerMap.get(parking.getOwnerId());
                data.setOwnerName(owner.getName());
                data.setRoomInfo(owner.getBuildingNo() + "-" + owner.getUnitNo() + "-" + owner.getRoomNo());
            }
            
            result.add(data);
        }
        
        return Result.success(result);
    }
    
    @GetMapping("/parkings/summary")
    public Result<ParkingSummary> getParkingSummary() {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        
        LambdaQueryWrapper<Parking> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Parking::getAccountSetId, accountSetId);
        long total = parkingMapper.selectCount(wrapper);
        
        wrapper.eq(Parking::getStatus, "USED");
        long used = parkingMapper.selectCount(wrapper);
        
        ParkingSummary summary = new ParkingSummary();
        summary.setTotal(total);
        summary.setUsed(used);
        summary.setVacant(total - used);
        
        return Result.success(summary);
    }
    
    @Data
    public static class BuildingDTO {
        @NotBlank(message = "楼栋号不能为空")
        private String buildingNo;
        private Integer unitCount;
        private Integer floorCount;
        private Integer roomsPerFloor;
        private Integer positionX;
        private Integer positionY;
    }
    
    @Data
    public static class BuildingLayoutData {
        private Long id;
        private String buildingNo;
        private Integer unitCount;
        private Integer floorCount;
        private Integer roomsPerFloor;
        private Integer positionX;
        private Integer positionY;
        private Integer totalRooms;
        private Integer occupiedRooms;
        private List<Owner> owners;
    }
    
    @Data
    public static class ParkingLayoutData {
        private Long id;
        private String parkingNo;
        private String status;
        private Long ownerId;
        private String ownerName;
        private String roomInfo;
    }
    
    @Data
    public static class ParkingSummary {
        private Long total;
        private Long used;
        private Long vacant;
    }
}
