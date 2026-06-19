package com.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.common.Result;
import com.property.config.AccountSetContext;
import com.property.entity.*;
import com.property.mapper.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/statistics")
@RequiredArgsConstructor
public class StatisticsController {
    
    private final BillMapper billMapper;
    private final PaymentMapper paymentMapper;
    private final OwnerMapper ownerMapper;
    private final ParkingMapper parkingMapper;
    private final BuildingMapper buildingMapper;
    private final SysConfigMapper sysConfigMapper;
    
    @Value("${property.arrears-threshold:20}")
    private BigDecimal defaultArrearsThreshold;
    
    @GetMapping("/dashboard")
    public Result<DashboardData> getDashboard() {
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        DashboardData data = new DashboardData();
        
        LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(Owner::getAccountSetId, accountSetId);
        data.setTotalOwners(ownerMapper.selectCount(ownerWrapper));
        
        ownerWrapper.eq(Owner::getStatus, "OCCUPIED");
        data.setOccupiedOwners(ownerMapper.selectCount(ownerWrapper));
        
        LambdaQueryWrapper<Parking> parkingWrapper = new LambdaQueryWrapper<>();
        parkingWrapper.eq(Parking::getAccountSetId, accountSetId);
        data.setTotalParkings(parkingMapper.selectCount(parkingWrapper));
        
        parkingWrapper.eq(Parking::getStatus, "USED");
        data.setUsedParkings(parkingMapper.selectCount(parkingWrapper));
        
        LambdaQueryWrapper<Bill> billWrapper = new LambdaQueryWrapper<>();
        billWrapper.eq(Bill::getAccountSetId, accountSetId);
        List<Bill> bills = billMapper.selectList(billWrapper);
        
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;
        long overdueCount = 0;
        
        for (Bill bill : bills) {
            totalAmount = totalAmount.add(bill.getAmount());
            paidAmount = paidAmount.add(bill.getPaidAmount());
            if ("OVERDUE".equals(bill.getStatus())) {
                overdueCount++;
            }
        }
        
        data.setTotalAmount(totalAmount);
        data.setPaidAmount(paidAmount);
        data.setUnpaidAmount(totalAmount.subtract(paidAmount));
        data.setOverdueCount(overdueCount);
        
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            data.setCollectionRate(paidAmount.multiply(BigDecimal.valueOf(100))
                    .divide(totalAmount, 2, RoundingMode.HALF_UP));
            data.setArrearsRate(data.getUnpaidAmount().multiply(BigDecimal.valueOf(100))
                    .divide(totalAmount, 2, RoundingMode.HALF_UP));
        } else {
            data.setCollectionRate(BigDecimal.ZERO);
            data.setArrearsRate(BigDecimal.ZERO);
        }
        
        // 计算账龄（逾期天数）
        LocalDate today = LocalDate.now();
        int totalOverdueDays = 0;
        int maxOverdueDays = 0;
        int overdueCountForAge = 0;
        for (Bill bill : bills) {
            if ("OVERDUE".equals(bill.getStatus()) && bill.getDueDate() != null) {
                int days = (int) java.time.temporal.ChronoUnit.DAYS.between(bill.getDueDate(), today);
                if (days > 0) {
                    totalOverdueDays += days;
                    maxOverdueDays = Math.max(maxOverdueDays, days);
                    overdueCountForAge++;
                }
            }
        }
        data.setAvgOverdueDays(overdueCountForAge > 0 ? totalOverdueDays / overdueCountForAge : 0);
        data.setMaxOverdueDays(maxOverdueDays);
        
        return Result.success(data);
    }
    
    @GetMapping("/payment-detail")
    public Result<List<PaymentDetail>> getPaymentDetail(
            @RequestParam String feeType,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        List<PaymentDetail> details = new ArrayList<>();
        
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Payment::getAccountSetId, accountSetId)
               .ge(Payment::getCreateTime, startDate.atStartOfDay())
               .le(Payment::getCreateTime, endDate.plusDays(1).atStartOfDay());
        
        List<Payment> payments = paymentMapper.selectList(wrapper);
        
        for (Payment payment : payments) {
            Bill bill = billMapper.selectById(payment.getBillId());
            if (bill != null && feeType.equals(bill.getFeeType())) {
                Owner owner = ownerMapper.selectById(payment.getOwnerId());
                
                PaymentDetail detail = new PaymentDetail();
                detail.setPaymentNo(payment.getPaymentNo());
                detail.setOwnerName(owner != null ? owner.getName() : "");
                detail.setRoomInfo(owner != null ? owner.getBuildingNo() + "-" + owner.getUnitNo() + "-" + owner.getRoomNo() : "");
                detail.setFeeName(bill.getFeeName());
                detail.setAmount(payment.getActualAmount());
                detail.setPaymentMethod(payment.getPaymentMethod());
                detail.setPaymentTime(payment.getCreateTime().toString());
                details.add(detail);
            }
        }
        
        return Result.success(details);
    }
    
    @GetMapping("/arrears-detail")
    public Result<List<ArrearsDetail>> getArrearsDetail(
            @RequestParam String feeType,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        List<ArrearsDetail> details = new ArrayList<>();
        
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bill::getAccountSetId, accountSetId)
               .eq(Bill::getFeeType, feeType)
               .in(Bill::getStatus, "UNPAID", "OVERDUE")
               .ge(Bill::getPeriodStart, startDate)
               .le(Bill::getPeriodEnd, endDate);
        
        List<Bill> bills = billMapper.selectList(wrapper);
        
        for (Bill bill : bills) {
            Owner owner = ownerMapper.selectById(bill.getOwnerId());
            
            ArrearsDetail detail = new ArrearsDetail();
            detail.setBillNo(bill.getBillNo());
            detail.setOwnerName(owner != null ? owner.getName() : "");
            detail.setRoomInfo(owner != null ? owner.getBuildingNo() + "-" + owner.getUnitNo() + "-" + owner.getRoomNo() : "");
            detail.setFeeName(bill.getFeeName());
            detail.setAmount(bill.getAmount().subtract(bill.getPaidAmount()));
            detail.setPeriod(bill.getPeriodStart() + " ~ " + bill.getPeriodEnd());
            detail.setDueDate(bill.getDueDate().toString());
            detail.setStatus(bill.getStatus());
            details.add(detail);
        }
        
        return Result.success(details);
    }
    
    @Data
    public static class DashboardData {
        private Long totalOwners;
        private Long occupiedOwners;
        private Long totalParkings;
        private Long usedParkings;
        private BigDecimal totalAmount;
        private BigDecimal paidAmount;
        private BigDecimal unpaidAmount;
        private Long overdueCount;
        private BigDecimal collectionRate;
        private BigDecimal arrearsRate;
        private Integer avgOverdueDays;
        private Integer maxOverdueDays;
    }
    
    @Data
    public static class PaymentDetail {
        private String paymentNo;
        private String ownerName;
        private String roomInfo;
        private String feeName;
        private BigDecimal amount;
        private String paymentMethod;
        private String paymentTime;
    }
    
    @Data
    public static class ArrearsDetail {
        private String billNo;
        private String ownerName;
        private String roomInfo;
        private String feeName;
        private BigDecimal amount;
        private String period;
        private String dueDate;
        private String status;
    }
    
    @GetMapping("/summary")
    public Result<SummaryData> getSummary(
            @RequestParam String feeType,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        SummaryData summary = new SummaryData();
        
        // 缴费汇总
        LambdaQueryWrapper<Payment> paymentWrapper = new LambdaQueryWrapper<>();
        paymentWrapper.eq(Payment::getAccountSetId, accountSetId)
                      .ge(Payment::getCreateTime, startDate.atStartOfDay())
                      .le(Payment::getCreateTime, endDate.plusDays(1).atStartOfDay());
        List<Payment> payments = paymentMapper.selectList(paymentWrapper);
        
        BigDecimal totalPaid = BigDecimal.ZERO;
        int paidCount = 0;
        for (Payment payment : payments) {
            Bill bill = billMapper.selectById(payment.getBillId());
            if (bill != null && feeType.equals(bill.getFeeType())) {
                totalPaid = totalPaid.add(payment.getActualAmount());
                paidCount++;
            }
        }
        summary.setTotalPaidAmount(totalPaid);
        summary.setPaidCount(paidCount);
        
        // 欠费汇总
        LambdaQueryWrapper<Bill> billWrapper = new LambdaQueryWrapper<>();
        billWrapper.eq(Bill::getAccountSetId, accountSetId)
                   .eq(Bill::getFeeType, feeType)
                   .in(Bill::getStatus, "UNPAID", "OVERDUE")
                   .ge(Bill::getPeriodStart, startDate)
                   .le(Bill::getPeriodEnd, endDate);
        List<Bill> bills = billMapper.selectList(billWrapper);
        
        BigDecimal totalArrears = BigDecimal.ZERO;
        for (Bill bill : bills) {
            totalArrears = totalArrears.add(bill.getAmount().subtract(bill.getPaidAmount()));
        }
        summary.setTotalArrearsAmount(totalArrears);
        summary.setArrearsCount(bills.size());
        
        return Result.success(summary);
    }
    
    @GetMapping("/payment-detail/export")
    public void exportPaymentDetail(
            @RequestParam String feeType,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            HttpServletResponse response) throws IOException {
        
        Result<List<PaymentDetail>> result = getPaymentDetail(feeType, startDate, endDate);
        List<PaymentDetail> details = result.getData();
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("缴费明细");
            String[] headers = {"缴费单号", "业主", "房间", "费用", "金额", "方式", "时间"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            
            int rowNum = 1;
            for (PaymentDetail detail : details) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(detail.getPaymentNo());
                row.createCell(1).setCellValue(detail.getOwnerName());
                row.createCell(2).setCellValue(detail.getRoomInfo());
                row.createCell(3).setCellValue(detail.getFeeName());
                row.createCell(4).setCellValue(detail.getAmount().doubleValue());
                row.createCell(5).setCellValue(detail.getPaymentMethod());
                row.createCell(6).setCellValue(detail.getPaymentTime());
            }
            
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + 
                URLEncoder.encode("缴费明细.xlsx", StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
    }
    
    @GetMapping("/arrears-detail/export")
    public void exportArrearsDetail(
            @RequestParam String feeType,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            HttpServletResponse response) throws IOException {
        
        Result<List<ArrearsDetail>> result = getArrearsDetail(feeType, startDate, endDate);
        List<ArrearsDetail> details = result.getData();
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("欠费明细");
            String[] headers = {"账单编号", "业主", "房间", "费用", "欠费金额", "周期", "截止日期"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            
            int rowNum = 1;
            for (ArrearsDetail detail : details) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(detail.getBillNo());
                row.createCell(1).setCellValue(detail.getOwnerName());
                row.createCell(2).setCellValue(detail.getRoomInfo());
                row.createCell(3).setCellValue(detail.getFeeName());
                row.createCell(4).setCellValue(detail.getAmount().doubleValue());
                row.createCell(5).setCellValue(detail.getPeriod());
                row.createCell(6).setCellValue(detail.getDueDate());
            }
            
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + 
                URLEncoder.encode("欠费明细.xlsx", StandardCharsets.UTF_8));
            workbook.write(response.getOutputStream());
        }
    }
    
    @Data
    public static class SummaryData {
        private BigDecimal totalPaidAmount;
        private Integer paidCount;
        private BigDecimal totalArrearsAmount;
        private Integer arrearsCount;
    }
    
    @GetMapping("/building-summary")
    public Result<List<BuildingSummary>> getBuildingSummary(
            @RequestParam String feeType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        BigDecimal threshold = getArrearsThreshold();
        
        List<Building> buildings = buildingMapper.selectList(
            new LambdaQueryWrapper<Building>()
                .eq(Building::getAccountSetId, accountSetId)
                .orderByAsc(Building::getBuildingNo)
        );
        
        LambdaQueryWrapper<Bill> billWrapper = new LambdaQueryWrapper<>();
        billWrapper.eq(Bill::getAccountSetId, accountSetId)
                   .eq(Bill::getFeeType, feeType);
        if (startDate != null) {
            billWrapper.ge(Bill::getPeriodStart, startDate);
        }
        if (endDate != null) {
            billWrapper.le(Bill::getPeriodEnd, endDate);
        }
        List<Bill> allBills = billMapper.selectList(billWrapper);
        
        Map<Long, Owner> ownerMap = ownerMapper.selectList(
            new LambdaQueryWrapper<Owner>().eq(Owner::getAccountSetId, accountSetId)
        ).stream().collect(Collectors.toMap(Owner::getId, o -> o));
        
        Map<String, List<Bill>> billsByBuilding = new HashMap<>();
        for (Bill bill : allBills) {
            Owner owner = ownerMap.get(bill.getOwnerId());
            if (owner != null) {
                billsByBuilding.computeIfAbsent(owner.getBuildingNo(), k -> new ArrayList<>()).add(bill);
            }
        }
        
        List<BuildingSummary> result = new ArrayList<>();
        for (Building building : buildings) {
            BuildingSummary summary = new BuildingSummary();
            summary.setBuildingNo(building.getBuildingNo());
            summary.setUnitCount(building.getUnitCount());
            
            List<Bill> buildingBills = billsByBuilding.getOrDefault(building.getBuildingNo(), Collections.emptyList());
            
            BigDecimal totalReceivable = BigDecimal.ZERO;
            BigDecimal totalPaid = BigDecimal.ZERO;
            for (Bill bill : buildingBills) {
                totalReceivable = totalReceivable.add(bill.getAmount());
                totalPaid = totalPaid.add(bill.getPaidAmount());
            }
            
            BigDecimal totalArrears = totalReceivable.subtract(totalPaid);
            summary.setTotalReceivable(totalReceivable);
            summary.setTotalPaid(totalPaid);
            summary.setTotalArrears(totalArrears);
            
            if (totalReceivable.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal collectionRate = totalPaid.multiply(BigDecimal.valueOf(100))
                    .divide(totalReceivable, 2, RoundingMode.HALF_UP);
                BigDecimal arrearsRate = totalArrears.multiply(BigDecimal.valueOf(100))
                    .divide(totalReceivable, 2, RoundingMode.HALF_UP);
                summary.setCollectionRate(collectionRate);
                summary.setArrearsRate(arrearsRate);
                summary.setHighlight(arrearsRate.compareTo(threshold) >= 0);
            } else {
                summary.setCollectionRate(BigDecimal.ZERO);
                summary.setArrearsRate(BigDecimal.ZERO);
                summary.setHighlight(false);
            }
            
            result.add(summary);
        }
        
        return Result.success(result);
    }
    
    @GetMapping("/building-detail/{buildingNo}")
    public Result<List<UnitSummary>> getBuildingDetail(
            @PathVariable String buildingNo,
            @RequestParam String feeType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        BigDecimal threshold = getArrearsThreshold();
        
        LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(Owner::getAccountSetId, accountSetId)
                    .eq(Owner::getBuildingNo, buildingNo)
                    .orderByAsc(Owner::getUnitNo, Owner::getRoomNo);
        List<Owner> owners = ownerMapper.selectList(ownerWrapper);
        
        LambdaQueryWrapper<Bill> billWrapper = new LambdaQueryWrapper<>();
        billWrapper.eq(Bill::getAccountSetId, accountSetId)
                   .eq(Bill::getFeeType, feeType);
        if (startDate != null) {
            billWrapper.ge(Bill::getPeriodStart, startDate);
        }
        if (endDate != null) {
            billWrapper.le(Bill::getPeriodEnd, endDate);
        }
        List<Bill> allBills = billMapper.selectList(billWrapper);
        
        Map<Long, List<Bill>> billsByOwner = allBills.stream()
            .collect(Collectors.groupingBy(Bill::getOwnerId));
        
        Map<String, List<Owner>> ownersByUnit = owners.stream()
            .collect(Collectors.groupingBy(Owner::getUnitNo, TreeMap::new, Collectors.toList()));
        
        List<UnitSummary> result = new ArrayList<>();
        for (Map.Entry<String, List<Owner>> entry : ownersByUnit.entrySet()) {
            String unitNo = entry.getKey();
            List<Owner> unitOwners = entry.getValue();
            
            UnitSummary unitSummary = new UnitSummary();
            unitSummary.setUnitNo(unitNo);
            
            BigDecimal unitReceivable = BigDecimal.ZERO;
            BigDecimal unitPaid = BigDecimal.ZERO;
            List<RoomDetail> rooms = new ArrayList<>();
            
            for (Owner owner : unitOwners) {
                RoomDetail room = new RoomDetail();
                room.setOwnerId(owner.getId());
                room.setOwnerName(owner.getName());
                room.setRoomNo(owner.getRoomNo());
                room.setPhone(owner.getPhone());
                
                List<Bill> ownerBills = billsByOwner.getOrDefault(owner.getId(), Collections.emptyList());
                BigDecimal roomReceivable = BigDecimal.ZERO;
                BigDecimal roomPaid = BigDecimal.ZERO;
                
                for (Bill bill : ownerBills) {
                    roomReceivable = roomReceivable.add(bill.getAmount());
                    roomPaid = roomPaid.add(bill.getPaidAmount());
                }
                
                BigDecimal roomArrears = roomReceivable.subtract(roomPaid);
                room.setTotalReceivable(roomReceivable);
                room.setTotalPaid(roomPaid);
                room.setTotalArrears(roomArrears);
                
                if (roomReceivable.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal collectionRate = roomPaid.multiply(BigDecimal.valueOf(100))
                        .divide(roomReceivable, 2, RoundingMode.HALF_UP);
                    BigDecimal arrearsRate = roomArrears.multiply(BigDecimal.valueOf(100))
                        .divide(roomReceivable, 2, RoundingMode.HALF_UP);
                    room.setCollectionRate(collectionRate);
                    room.setArrearsRate(arrearsRate);
                } else {
                    room.setCollectionRate(BigDecimal.ZERO);
                    room.setArrearsRate(BigDecimal.ZERO);
                }
                
                rooms.add(room);
                unitReceivable = unitReceivable.add(roomReceivable);
                unitPaid = unitPaid.add(roomPaid);
            }
            
            BigDecimal unitArrears = unitReceivable.subtract(unitPaid);
            unitSummary.setTotalReceivable(unitReceivable);
            unitSummary.setTotalPaid(unitPaid);
            unitSummary.setTotalArrears(unitArrears);
            unitSummary.setRooms(rooms);
            
            if (unitReceivable.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal collectionRate = unitPaid.multiply(BigDecimal.valueOf(100))
                    .divide(unitReceivable, 2, RoundingMode.HALF_UP);
                BigDecimal arrearsRate = unitArrears.multiply(BigDecimal.valueOf(100))
                    .divide(unitReceivable, 2, RoundingMode.HALF_UP);
                unitSummary.setCollectionRate(collectionRate);
                unitSummary.setArrearsRate(arrearsRate);
                unitSummary.setHighlight(arrearsRate.compareTo(threshold) >= 0);
            } else {
                unitSummary.setCollectionRate(BigDecimal.ZERO);
                unitSummary.setArrearsRate(BigDecimal.ZERO);
                unitSummary.setHighlight(false);
            }
            
            result.add(unitSummary);
        }
        
        return Result.success(result);
    }
    
    @GetMapping("/arrears-threshold")
    public Result<BigDecimal> getArrearsThresholdConfig() {
        return Result.success(getArrearsThreshold());
    }
    
    private BigDecimal getArrearsThreshold() {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysConfig::getConfigKey, "arrears_threshold");
        SysConfig config = sysConfigMapper.selectOne(wrapper);
        if (config != null && config.getConfigValue() != null) {
            try {
                return new BigDecimal(config.getConfigValue());
            } catch (NumberFormatException e) {
                return defaultArrearsThreshold;
            }
        }
        return defaultArrearsThreshold;
    }
    
    @Data
    public static class BuildingSummary {
        private String buildingNo;
        private Integer unitCount;
        private BigDecimal totalReceivable;
        private BigDecimal totalPaid;
        private BigDecimal totalArrears;
        private BigDecimal collectionRate;
        private BigDecimal arrearsRate;
        private Boolean highlight;
    }
    
    @Data
    public static class UnitSummary {
        private String unitNo;
        private BigDecimal totalReceivable;
        private BigDecimal totalPaid;
        private BigDecimal totalArrears;
        private BigDecimal collectionRate;
        private BigDecimal arrearsRate;
        private Boolean highlight;
        private List<RoomDetail> rooms;
    }
    
    @Data
    public static class RoomDetail {
        private Long ownerId;
        private String ownerName;
        private String roomNo;
        private String phone;
        private BigDecimal totalReceivable;
        private BigDecimal totalPaid;
        private BigDecimal totalArrears;
        private BigDecimal collectionRate;
        private BigDecimal arrearsRate;
    }
}
