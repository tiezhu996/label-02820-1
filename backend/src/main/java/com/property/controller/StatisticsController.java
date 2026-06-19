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
    private final SysConfigMapper sysConfigMapper;
    private final BuildingMapper buildingMapper;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String CONFIG_KEY_ARREARS_THRESHOLD = "building_arrears_threshold";
    private static final String DEFAULT_ARREARS_THRESHOLD = "30";
    
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

    /**
     * 楼栋汇总缴费率视图。
     * 入参：feeType（可选，传则限定费用类型）、startDate/endDate（可选，限定账单周期），
     *      threshold（可选，欠费率高亮阈值，单位 %，缺省读取 sys_config）。
     * 数据严格按当前账套隔离，欠费率超过阈值的楼栋 highlighted=true 由前端高亮。
     */
    @GetMapping("/building-collection")
    public Result<BuildingCollectionResult> getBuildingCollection(
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
            @RequestParam(required = false) BigDecimal threshold) {

        Long accountSetId = AccountSetContext.getCurrentAccountSetId();

        BigDecimal effectiveThreshold = threshold != null ? threshold : resolveArrearsThreshold();

        LambdaQueryWrapper<Building> buildingWrapper = new LambdaQueryWrapper<>();
        buildingWrapper.eq(Building::getAccountSetId, accountSetId);
        List<Building> buildings = buildingMapper.selectList(buildingWrapper);

        Set<String> buildingNos = new LinkedHashSet<>();
        buildings.stream()
                .sorted(Comparator.comparing(Building::getBuildingNo))
                .forEach(b -> buildingNos.add(b.getBuildingNo()));

        List<Bill> bills = queryBills(accountSetId, feeType, startDate, endDate);
        Map<Long, Owner> ownerMap = loadOwnerMap(bills);

        Map<String, List<Bill>> byBuilding = new HashMap<>();
        for (Bill bill : bills) {
            Owner owner = ownerMap.get(bill.getOwnerId());
            if (owner == null) continue;
            String buildingNo = owner.getBuildingNo();
            if (buildingNo == null) continue;
            buildingNos.add(buildingNo);
            byBuilding.computeIfAbsent(buildingNo, k -> new ArrayList<>()).add(bill);
        }

        List<BuildingCollectionRow> rows = new ArrayList<>();
        for (String buildingNo : buildingNos) {
            List<Bill> buildingBills = byBuilding.getOrDefault(buildingNo, Collections.emptyList());
            BuildingCollectionRow row = aggregateRow(buildingBills);
            row.setBuildingNo(buildingNo);
            row.setHighlighted(row.getArrearsRate().compareTo(effectiveThreshold) > 0);
            rows.add(row);
        }
        rows.sort(Comparator.comparing(BuildingCollectionRow::getBuildingNo));

        BuildingCollectionResult result = new BuildingCollectionResult();
        result.setThreshold(effectiveThreshold);
        result.setBuildings(rows);
        return Result.success(result);
    }

    /**
     * 楼栋下钻：按单元 + 单户输出明细，仍走账套隔离。
     */
    @GetMapping("/building-collection/{buildingNo}/units")
    public Result<List<UnitCollectionRow>> getBuildingUnits(
            @PathVariable String buildingNo,
            @RequestParam(required = false) String feeType,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {

        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        List<Bill> bills = queryBills(accountSetId, feeType, startDate, endDate);
        if (bills.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        Map<Long, Owner> ownerMap = loadOwnerMap(bills);

        List<Bill> filtered = bills.stream()
                .filter(bill -> {
                    Owner o = ownerMap.get(bill.getOwnerId());
                    return o != null && buildingNo.equals(o.getBuildingNo());
                })
                .toList();

        Map<String, List<Bill>> byUnit = filtered.stream()
                .collect(Collectors.groupingBy(bill -> ownerMap.get(bill.getOwnerId()).getUnitNo()));

        List<UnitCollectionRow> units = new ArrayList<>();
        byUnit.forEach((unitNo, unitBills) -> {
            UnitCollectionRow unitRow = new UnitCollectionRow();
            BuildingCollectionRow agg = aggregateRow(unitBills);
            unitRow.setBuildingNo(buildingNo);
            unitRow.setUnitNo(unitNo);
            unitRow.setReceivableAmount(agg.getReceivableAmount());
            unitRow.setPaidAmount(agg.getPaidAmount());
            unitRow.setArrearsAmount(agg.getArrearsAmount());
            unitRow.setCollectionRate(agg.getCollectionRate());
            unitRow.setArrearsRate(agg.getArrearsRate());

            Map<Long, List<Bill>> byOwner = unitBills.stream()
                    .collect(Collectors.groupingBy(Bill::getOwnerId));
            List<RoomCollectionRow> rooms = new ArrayList<>();
            byOwner.forEach((ownerId, ownerBills) -> {
                Owner owner = ownerMap.get(ownerId);
                if (owner == null) return;
                BuildingCollectionRow ownerAgg = aggregateRow(ownerBills);
                RoomCollectionRow roomRow = new RoomCollectionRow();
                roomRow.setOwnerId(ownerId);
                roomRow.setOwnerName(owner.getName());
                roomRow.setBuildingNo(owner.getBuildingNo());
                roomRow.setUnitNo(owner.getUnitNo());
                roomRow.setRoomNo(owner.getRoomNo());
                roomRow.setReceivableAmount(ownerAgg.getReceivableAmount());
                roomRow.setPaidAmount(ownerAgg.getPaidAmount());
                roomRow.setArrearsAmount(ownerAgg.getArrearsAmount());
                roomRow.setCollectionRate(ownerAgg.getCollectionRate());
                roomRow.setArrearsRate(ownerAgg.getArrearsRate());
                rooms.add(roomRow);
            });
            rooms.sort(Comparator.comparing(RoomCollectionRow::getRoomNo));
            unitRow.setRooms(rooms);
            units.add(unitRow);
        });
        units.sort(Comparator.comparing(UnitCollectionRow::getUnitNo));
        return Result.success(units);
    }

    private List<Bill> queryBills(Long accountSetId, String feeType, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bill::getAccountSetId, accountSetId);
        if (feeType != null && !feeType.isBlank()) {
            wrapper.eq(Bill::getFeeType, feeType);
        }
        if (startDate != null) {
            wrapper.ge(Bill::getPeriodStart, startDate);
        }
        if (endDate != null) {
            wrapper.le(Bill::getPeriodEnd, endDate);
        }
        return billMapper.selectList(wrapper);
    }

    private Map<Long, Owner> loadOwnerMap(List<Bill> bills) {
        Set<Long> ownerIds = bills.stream().map(Bill::getOwnerId).collect(Collectors.toSet());
        if (ownerIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Long accountSetId = AccountSetContext.getCurrentAccountSetId();
        LambdaQueryWrapper<Owner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Owner::getAccountSetId, accountSetId).in(Owner::getId, ownerIds);
        return ownerMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(Owner::getId, o -> o));
    }

    private BuildingCollectionRow aggregateRow(List<Bill> bills) {
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        for (Bill bill : bills) {
            receivable = receivable.add(bill.getAmount());
            paid = paid.add(bill.getPaidAmount());
        }
        BigDecimal arrears = receivable.subtract(paid);
        BuildingCollectionRow row = new BuildingCollectionRow();
        row.setReceivableAmount(receivable);
        row.setPaidAmount(paid);
        row.setArrearsAmount(arrears);
        if (receivable.compareTo(BigDecimal.ZERO) > 0) {
            row.setCollectionRate(paid.multiply(HUNDRED).divide(receivable, 2, RoundingMode.HALF_UP));
            row.setArrearsRate(arrears.multiply(HUNDRED).divide(receivable, 2, RoundingMode.HALF_UP));
        } else {
            row.setCollectionRate(BigDecimal.ZERO);
            row.setArrearsRate(BigDecimal.ZERO);
        }
        return row;
    }

    private BigDecimal resolveArrearsThreshold() {
        String value = sysConfigMapper.getValueByKey(CONFIG_KEY_ARREARS_THRESHOLD);
        try {
            return new BigDecimal(value != null ? value : DEFAULT_ARREARS_THRESHOLD);
        } catch (NumberFormatException ex) {
            return new BigDecimal(DEFAULT_ARREARS_THRESHOLD);
        }
    }

    @Data
    public static class BuildingCollectionResult {
        private BigDecimal threshold;
        private List<BuildingCollectionRow> buildings;
    }

    @Data
    public static class BuildingCollectionRow {
        private String buildingNo;
        private BigDecimal receivableAmount;
        private BigDecimal paidAmount;
        private BigDecimal arrearsAmount;
        private BigDecimal collectionRate;
        private BigDecimal arrearsRate;
        private Boolean highlighted;
    }

    @Data
    public static class UnitCollectionRow {
        private String buildingNo;
        private String unitNo;
        private BigDecimal receivableAmount;
        private BigDecimal paidAmount;
        private BigDecimal arrearsAmount;
        private BigDecimal collectionRate;
        private BigDecimal arrearsRate;
        private List<RoomCollectionRow> rooms;
    }

    @Data
    public static class RoomCollectionRow {
        private Long ownerId;
        private String ownerName;
        private String buildingNo;
        private String unitNo;
        private String roomNo;
        private BigDecimal receivableAmount;
        private BigDecimal paidAmount;
        private BigDecimal arrearsAmount;
        private BigDecimal collectionRate;
        private BigDecimal arrearsRate;
    }
}
