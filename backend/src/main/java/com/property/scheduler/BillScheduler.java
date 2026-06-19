package com.property.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.property.entity.*;
import com.property.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillScheduler {
    
    private final BillMapper billMapper;
    private final OwnerMapper ownerMapper;
    private final ParkingMapper parkingMapper;
    private final FeeStandardMapper feeStandardMapper;
    private final ReceivableMapper receivableMapper;
    private final SysAccountSetMapper accountSetMapper;
    private final BillScheduleMapper billScheduleMapper;
    private final SysConfigMapper configMapper;
    
    /**
     * 每天凌晨1点检查过期账单，标记为欠费
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void markOverdueBills() {
        log.info("开始检查过期账单...");
        LocalDate today = LocalDate.now();
        
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bill::getStatus, "UNPAID")
               .lt(Bill::getDueDate, today);
        
        List<Bill> overdueBills = billMapper.selectList(wrapper);
        for (Bill bill : overdueBills) {
            bill.setStatus("OVERDUE");
            billMapper.updateById(bill);
        }
        
        log.info("过期账单标记完成，共处理 {} 条", overdueBills.size());
    }
    
    /**
     * 每天凌晨2点检查是否需要锁定应收账款（根据配置）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void checkAndLockReceivables() {
        log.info("检查应收账款锁定...");
        LocalDate today = LocalDate.now();
        int dayOfMonth = today.getDayOfMonth();
        
        // 默认25日锁定，可通过配置调整
        if (dayOfMonth == 25) {
            lockReceivables();
        }
    }
    
    private void lockReceivables() {
        log.info("开始锁定当月应收账款...");
        String currentMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        
        LambdaQueryWrapper<Receivable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Receivable::getPeriodMonth, currentMonth)
               .eq(Receivable::getIsLocked, 0);
        
        List<Receivable> receivables = receivableMapper.selectList(wrapper);
        for (Receivable receivable : receivables) {
            receivable.setIsLocked(1);
            receivableMapper.updateById(receivable);
        }
        
        log.info("应收账款锁定完成，共锁定 {} 条", receivables.size());
    }
    
    /**
     * 每天凌晨3点检查是否需要生成账单（根据配置的生成日期）
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void checkAndGenerateBills() {
        log.info("检查账单生成任务...");
        LocalDate today = LocalDate.now();
        int dayOfMonth = today.getDayOfMonth();
        
        List<SysAccountSet> accountSets = accountSetMapper.selectList(
            new LambdaQueryWrapper<SysAccountSet>().eq(SysAccountSet::getStatus, 1)
        );
        
        for (SysAccountSet accountSet : accountSets) {
            // 获取该账套的调度配置
            LambdaQueryWrapper<BillSchedule> scheduleWrapper = new LambdaQueryWrapper<>();
            scheduleWrapper.eq(BillSchedule::getAccountSetId, accountSet.getId())
                          .eq(BillSchedule::getStatus, 1)
                          .eq(BillSchedule::getGenerateDay, dayOfMonth);
            List<BillSchedule> schedules = billScheduleMapper.selectList(scheduleWrapper);
            
            for (BillSchedule schedule : schedules) {
                generateBillsBySchedule(accountSet.getId(), schedule);
            }
        }
        
        // 如果没有配置，使用默认逻辑（每月1日生成）
        if (dayOfMonth == 1) {
            generateDefaultMonthlyBills();
        }
    }
    
    /**
     * 根据调度配置生成账单
     */
    private void generateBillsBySchedule(Long accountSetId, BillSchedule schedule) {
        log.info("根据配置生成账单: accountSetId={}, feeType={}, periodType={}", 
                 accountSetId, schedule.getFeeType(), schedule.getPeriodType());
        
        LocalDate today = LocalDate.now();
        LocalDate periodStart;
        LocalDate periodEnd;
        
        // 根据周期类型计算账单周期
        switch (schedule.getPeriodType()) {
            case "QUARTERLY" -> {
                int quarter = (today.getMonthValue() - 1) / 3;
                periodStart = today.withMonth(quarter * 3 + 1).withDayOfMonth(1);
                periodEnd = periodStart.plusMonths(3).minusDays(1);
            }
            case "YEARLY" -> {
                periodStart = today.withMonth(1).withDayOfMonth(1);
                periodEnd = today.withMonth(12).withDayOfMonth(31);
            }
            default -> { // MONTHLY
                periodStart = today.withDayOfMonth(1);
                periodEnd = today.withDayOfMonth(today.lengthOfMonth());
            }
        }
        
        LocalDate dueDate = periodEnd.plusDays(schedule.getDueDays());
        
        // 获取对应的收费标准
        String matchFeeType = "CUSTOM".equals(schedule.getFeeType()) && schedule.getCustomFeeType() != null
                ? schedule.getCustomFeeType() : schedule.getFeeType();
        LambdaQueryWrapper<FeeStandard> feeWrapper = new LambdaQueryWrapper<>();
        feeWrapper.eq(FeeStandard::getAccountSetId, accountSetId)
                  .eq(FeeStandard::getFeeType, matchFeeType)
                  .eq(FeeStandard::getStatus, 1);
        FeeStandard feeStandard = feeStandardMapper.selectOne(feeWrapper);

        if (feeStandard == null) return;

        // 计算周期月数
        int periodMonths = calculatePeriodMonths(periodStart, periodEnd);

        if ("PROPERTY".equals(schedule.getFeeType())) {
            generatePropertyBillsWithPeriod(accountSetId, feeStandard, periodStart, periodEnd, dueDate, periodMonths);
        } else if ("PARKING".equals(schedule.getFeeType())) {
            generateParkingBillsWithPeriod(accountSetId, feeStandard, periodStart, periodEnd, dueDate, periodMonths);
        } else {
            generateCustomBillsWithPeriod(accountSetId, feeStandard, periodStart, periodEnd, dueDate, periodMonths);
        }
    }
    
    /**
     * 默认每月账单生成（无配置时使用，根据收费标准的frequency自动判断周期）
     */
    private void generateDefaultMonthlyBills() {
        log.info("开始默认账单生成...");
        LocalDate today = LocalDate.now();
        
        List<SysAccountSet> accountSets = accountSetMapper.selectList(
            new LambdaQueryWrapper<SysAccountSet>().eq(SysAccountSet::getStatus, 1)
        );
        
        int totalCount = 0;
        for (SysAccountSet accountSet : accountSets) {
            // 检查是否有自定义配置，有则跳过默认生成
            LambdaQueryWrapper<BillSchedule> checkWrapper = new LambdaQueryWrapper<>();
            checkWrapper.eq(BillSchedule::getAccountSetId, accountSet.getId())
                       .eq(BillSchedule::getStatus, 1);
            if (billScheduleMapper.selectCount(checkWrapper) > 0) {
                continue;
            }
            totalCount += generateBillsByFrequency(accountSet.getId(), today);
        }
        
        log.info("默认账单生成完成，共生成 {} 条", totalCount);
    }
    
    /**
     * 根据收费标准的frequency自动生成账单
     */
    private int generateBillsByFrequency(Long accountSetId, LocalDate today) {
        int count = 0;
        
        // 获取所有启用的收费标准
        LambdaQueryWrapper<FeeStandard> feeWrapper = new LambdaQueryWrapper<>();
        feeWrapper.eq(FeeStandard::getAccountSetId, accountSetId)
                  .eq(FeeStandard::getStatus, 1);
        List<FeeStandard> feeStandards = feeStandardMapper.selectList(feeWrapper);
        
        // 从系统配置读取默认到期天数
        int defaultDueDays = getDefaultDueDays();
        
        for (FeeStandard fee : feeStandards) {
            String frequency = fee.getFrequency();
            if (frequency == null) frequency = "MONTHLY";
            
            // 根据频次判断是否需要在当前时间生成
            LocalDate periodStart;
            LocalDate periodEnd;
            int periodMonths;
            
            switch (frequency) {
                case "QUARTERLY" -> {
                    // 季度首月（1、4、7、10月）的1日生成
                    if (today.getMonthValue() % 3 != 1 || today.getDayOfMonth() != 1) continue;
                    int quarter = (today.getMonthValue() - 1) / 3;
                    periodStart = today.withMonth(quarter * 3 + 1).withDayOfMonth(1);
                    periodEnd = periodStart.plusMonths(3).minusDays(1);
                    periodMonths = 3;
                }
                case "YEARLY" -> {
                    // 每年1月1日生成
                    if (today.getMonthValue() != 1 || today.getDayOfMonth() != 1) continue;
                    periodStart = today.withMonth(1).withDayOfMonth(1);
                    periodEnd = today.withMonth(12).withDayOfMonth(31);
                    periodMonths = 12;
                }
                case "ONETIME" -> {
                    // 一次性：检查是否已生成过
                    LambdaQueryWrapper<Bill> checkWrapper = new LambdaQueryWrapper<>();
                    checkWrapper.eq(Bill::getAccountSetId, accountSetId)
                               .eq(Bill::getFeeType, fee.getFeeType());
                    if (billMapper.selectCount(checkWrapper) > 0) continue;
                    periodStart = today.withDayOfMonth(1);
                    periodEnd = today.withDayOfMonth(today.lengthOfMonth());
                    periodMonths = 1;
                }
                default -> { // MONTHLY
                    periodStart = today.withDayOfMonth(1);
                    periodEnd = today.withDayOfMonth(today.lengthOfMonth());
                    periodMonths = 1;
                }
            }
            
            LocalDate dueDate = periodEnd.plusDays(defaultDueDays);
            
            if ("PROPERTY".equals(fee.getFeeType())) {
                count += generatePropertyBillsWithPeriod(accountSetId, fee, periodStart, periodEnd, dueDate, periodMonths);
            } else if ("PARKING".equals(fee.getFeeType())) {
                count += generateParkingBillsWithPeriod(accountSetId, fee, periodStart, periodEnd, dueDate, periodMonths);
            } else {
                count += generateCustomBillsWithPeriod(accountSetId, fee, periodStart, periodEnd, dueDate, periodMonths);
            }
        }
        
        return count;
    }
    
    private int generateBillsForAccountSet(Long accountSetId, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate) {
        int count = 0;
        
        // 获取所有启用的收费标准
        LambdaQueryWrapper<FeeStandard> feeWrapper = new LambdaQueryWrapper<>();
        feeWrapper.eq(FeeStandard::getAccountSetId, accountSetId)
                  .eq(FeeStandard::getStatus, 1);
        List<FeeStandard> feeStandards = feeStandardMapper.selectList(feeWrapper);
        
        for (FeeStandard fee : feeStandards) {
            // 根据收费频次判断是否需要生成
            if (!shouldGenerateBill(fee.getFrequency(), fee.getFeeType(), accountSetId, periodStart)) {
                continue;
            }
            
            if ("PROPERTY".equals(fee.getFeeType())) {
                count += generatePropertyBills(accountSetId, fee, periodStart, periodEnd, dueDate);
            } else if ("PARKING".equals(fee.getFeeType())) {
                count += generateParkingBills(accountSetId, fee, periodStart, periodEnd, dueDate);
            } else {
                // 自定义收费项目：为所有业主生成账单
                count += generateCustomBills(accountSetId, fee, periodStart, periodEnd, dueDate);
            }
        }
        
        return count;
    }
    
    /**
     * 判断是否需要生成账单
     * @param frequency 收费频次: MONTHLY/QUARTERLY/YEARLY/ONETIME
     * @param feeType 费用类型
     * @param accountSetId 账套ID
     * @param periodStart 账单周期开始日期
     * @return 是否需要生成
     */
    private boolean shouldGenerateBill(String frequency, String feeType, Long accountSetId, LocalDate periodStart) {
        if (frequency == null || "MONTHLY".equals(frequency)) {
            // 按月收费：每月都生成
            return true;
        }
        if ("QUARTERLY".equals(frequency)) {
            // 按季度收费：仅在季度首月生成
            return periodStart.getMonthValue() % 3 == 1;
        }
        if ("YEARLY".equals(frequency)) {
            // 按年收费：仅在1月生成
            return periodStart.getMonthValue() == 1;
        }
        if ("ONETIME".equals(frequency)) {
            // 一次性收费：检查是否已经生成过该类型的账单
            LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Bill::getAccountSetId, accountSetId)
                   .eq(Bill::getFeeType, feeType);
            return billMapper.selectCount(wrapper) == 0;
        }
        return true;
    }
    
    private int generatePropertyBills(Long accountSetId, FeeStandard fee, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate) {
        int count = 0;
        LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(Owner::getAccountSetId, accountSetId);
        List<Owner> owners = ownerMapper.selectList(ownerWrapper);
        
        for (Owner owner : owners) {
            // 检查是否已存在该周期账单
            if (billExists(accountSetId, owner.getId(), null, fee.getFeeType(), periodStart, periodEnd)) {
                continue;
            }
            
            Bill bill = new Bill();
            bill.setAccountSetId(accountSetId);
            bill.setBillNo(generateBillNo());
            bill.setOwnerId(owner.getId());
            bill.setFeeType(fee.getFeeType());
            bill.setFeeName(fee.getFeeName());
            bill.setAmount(owner.getArea().multiply(fee.getAmount()));
            bill.setPaidAmount(BigDecimal.ZERO);
            bill.setPeriodStart(periodStart);
            bill.setPeriodEnd(periodEnd);
            bill.setDueDate(dueDate);
            bill.setStatus("UNPAID");
            billMapper.insert(bill);
            count++;
        }
        return count;
    }
    
    private int generateParkingBills(Long accountSetId, FeeStandard fee, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate) {
        int count = 0;
        LambdaQueryWrapper<Parking> parkingWrapper = new LambdaQueryWrapper<>();
        parkingWrapper.eq(Parking::getAccountSetId, accountSetId)
                      .isNotNull(Parking::getOwnerId);
        List<Parking> parkings = parkingMapper.selectList(parkingWrapper);
        
        for (Parking parking : parkings) {
            if (billExists(accountSetId, parking.getOwnerId(), parking.getId(), fee.getFeeType(), periodStart, periodEnd)) {
                continue;
            }
            
            Bill bill = new Bill();
            bill.setAccountSetId(accountSetId);
            bill.setBillNo(generateBillNo());
            bill.setOwnerId(parking.getOwnerId());
            bill.setParkingId(parking.getId());
            bill.setFeeType(fee.getFeeType());
            bill.setFeeName(fee.getFeeName());
            bill.setAmount(fee.getAmount());
            bill.setPaidAmount(BigDecimal.ZERO);
            bill.setPeriodStart(periodStart);
            bill.setPeriodEnd(periodEnd);
            bill.setDueDate(dueDate);
            bill.setStatus("UNPAID");
            billMapper.insert(bill);
            count++;
        }
        return count;
    }
    
    /**
     * 生成自定义收费项目账单（按固定金额为所有业主生成）
     */
    private int generateCustomBills(Long accountSetId, FeeStandard fee, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate) {
        return generateCustomBillsWithPeriod(accountSetId, fee, periodStart, periodEnd, dueDate, 1);
    }
    
    /**
     * 生成物业费账单（带周期月数）
     */
    private int generatePropertyBillsWithPeriod(Long accountSetId, FeeStandard fee, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate, int periodMonths) {
        int count = 0;
        LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(Owner::getAccountSetId, accountSetId);
        List<Owner> owners = ownerMapper.selectList(ownerWrapper);
        
        for (Owner owner : owners) {
            if (billExists(accountSetId, owner.getId(), null, fee.getFeeType(), periodStart, periodEnd)) {
                continue;
            }
            
            Bill bill = new Bill();
            bill.setAccountSetId(accountSetId);
            bill.setBillNo(generateBillNo());
            bill.setOwnerId(owner.getId());
            bill.setFeeType(fee.getFeeType());
            bill.setFeeName(fee.getFeeName());
            // 金额 = 面积 × 单价 × 周期月数
            bill.setAmount(owner.getArea().multiply(fee.getAmount()).multiply(BigDecimal.valueOf(periodMonths)));
            bill.setPaidAmount(BigDecimal.ZERO);
            bill.setPeriodStart(periodStart);
            bill.setPeriodEnd(periodEnd);
            bill.setDueDate(dueDate);
            bill.setStatus("UNPAID");
            billMapper.insert(bill);
            count++;
        }
        return count;
    }
    
    /**
     * 生成车位费账单（带周期月数）
     */
    private int generateParkingBillsWithPeriod(Long accountSetId, FeeStandard fee, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate, int periodMonths) {
        int count = 0;
        LambdaQueryWrapper<Parking> parkingWrapper = new LambdaQueryWrapper<>();
        parkingWrapper.eq(Parking::getAccountSetId, accountSetId)
                      .isNotNull(Parking::getOwnerId);
        List<Parking> parkings = parkingMapper.selectList(parkingWrapper);
        
        for (Parking parking : parkings) {
            if (billExists(accountSetId, parking.getOwnerId(), parking.getId(), fee.getFeeType(), periodStart, periodEnd)) {
                continue;
            }
            
            Bill bill = new Bill();
            bill.setAccountSetId(accountSetId);
            bill.setBillNo(generateBillNo());
            bill.setOwnerId(parking.getOwnerId());
            bill.setParkingId(parking.getId());
            bill.setFeeType(fee.getFeeType());
            bill.setFeeName(fee.getFeeName());
            // 金额 = 单价 × 周期月数
            bill.setAmount(fee.getAmount().multiply(BigDecimal.valueOf(periodMonths)));
            bill.setPaidAmount(BigDecimal.ZERO);
            bill.setPeriodStart(periodStart);
            bill.setPeriodEnd(periodEnd);
            bill.setDueDate(dueDate);
            bill.setStatus("UNPAID");
            billMapper.insert(bill);
            count++;
        }
        return count;
    }
    
    /**
     * 生成自定义收费项目账单（带周期月数）
     */
    private int generateCustomBillsWithPeriod(Long accountSetId, FeeStandard fee, LocalDate periodStart, LocalDate periodEnd, LocalDate dueDate, int periodMonths) {
        int count = 0;
        LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(Owner::getAccountSetId, accountSetId);
        List<Owner> owners = ownerMapper.selectList(ownerWrapper);
        
        for (Owner owner : owners) {
            if (billExists(accountSetId, owner.getId(), null, fee.getFeeType(), periodStart, periodEnd)) {
                continue;
            }
            
            Bill bill = new Bill();
            bill.setAccountSetId(accountSetId);
            bill.setBillNo(generateBillNo());
            bill.setOwnerId(owner.getId());
            bill.setFeeType(fee.getFeeType());
            bill.setFeeName(fee.getFeeName());
            // 金额 = 固定金额 × 周期月数
            bill.setAmount(fee.getAmount().multiply(BigDecimal.valueOf(periodMonths)));
            bill.setPaidAmount(BigDecimal.ZERO);
            bill.setPeriodStart(periodStart);
            bill.setPeriodEnd(periodEnd);
            bill.setDueDate(dueDate);
            bill.setStatus("UNPAID");
            billMapper.insert(bill);
            count++;
        }
        return count;
    }
    
    /**
     * 计算周期月数
     */
    private int calculatePeriodMonths(LocalDate start, LocalDate end) {
        if (start == null || end == null) return 1;
        int months = (end.getYear() - start.getYear()) * 12 + (end.getMonthValue() - start.getMonthValue()) + 1;
        return Math.max(1, months);
    }
    
    private boolean billExists(Long accountSetId, Long ownerId, Long parkingId, String feeType, LocalDate periodStart, LocalDate periodEnd) {
        LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Bill::getAccountSetId, accountSetId)
               .eq(Bill::getOwnerId, ownerId)
               .eq(Bill::getFeeType, feeType)
               .eq(Bill::getPeriodStart, periodStart)
               .eq(Bill::getPeriodEnd, periodEnd);
        if (parkingId != null) {
            wrapper.eq(Bill::getParkingId, parkingId);
        }
        return billMapper.selectCount(wrapper) > 0;
    }
    
    /**
     * 每月26日生成次月应收账款
     */
    @Scheduled(cron = "0 0 1 26 * ?")
    public void generateNextMonthReceivables() {
        log.info("开始生成次月应收账款...");
        LocalDate nextMonth = LocalDate.now().plusMonths(1);
        String periodMonth = nextMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        
        List<SysAccountSet> accountSets = accountSetMapper.selectList(
            new LambdaQueryWrapper<SysAccountSet>().eq(SysAccountSet::getStatus, 1)
        );
        
        for (SysAccountSet accountSet : accountSets) {
            generateReceivablesForAccountSet(accountSet.getId(), periodMonth);
        }
        
        log.info("次月应收账款生成完成");
    }
    
    private void generateReceivablesForAccountSet(Long accountSetId, String periodMonth) {
        LambdaQueryWrapper<FeeStandard> feeWrapper = new LambdaQueryWrapper<>();
        feeWrapper.eq(FeeStandard::getAccountSetId, accountSetId)
                  .eq(FeeStandard::getStatus, 1);
        List<FeeStandard> feeStandards = feeStandardMapper.selectList(feeWrapper);
        
        LambdaQueryWrapper<Owner> ownerWrapper = new LambdaQueryWrapper<>();
        ownerWrapper.eq(Owner::getAccountSetId, accountSetId);
        List<Owner> owners = ownerMapper.selectList(ownerWrapper);
        
        for (Owner owner : owners) {
            for (FeeStandard fee : feeStandards) {
                if ("PROPERTY".equals(fee.getFeeType())) {
                    // 一次性收费检查是否已生成过
                    if ("ONETIME".equals(fee.getFrequency()) && hasReceivableForFeeType(accountSetId, owner.getId(), null, fee.getFeeType())) {
                        continue;
                    }
                    createReceivable(accountSetId, owner.getId(), null, fee, owner.getArea(), periodMonth);
                } else if (!"PARKING".equals(fee.getFeeType())) {
                    // 自定义收费项目：为所有业主生成应收账款
                    if ("ONETIME".equals(fee.getFrequency()) && hasReceivableForFeeType(accountSetId, owner.getId(), null, fee.getFeeType())) {
                        continue;
                    }
                    createReceivable(accountSetId, owner.getId(), null, fee, BigDecimal.ONE, periodMonth);
                }
            }
        }
        
        LambdaQueryWrapper<Parking> parkingWrapper = new LambdaQueryWrapper<>();
        parkingWrapper.eq(Parking::getAccountSetId, accountSetId)
                      .isNotNull(Parking::getOwnerId);
        List<Parking> parkings = parkingMapper.selectList(parkingWrapper);
        
        for (Parking parking : parkings) {
            for (FeeStandard fee : feeStandards) {
                if ("PARKING".equals(fee.getFeeType())) {
                    // 一次性收费检查是否已生成过
                    if ("ONETIME".equals(fee.getFrequency()) && hasReceivableForFeeType(accountSetId, parking.getOwnerId(), parking.getId(), fee.getFeeType())) {
                        continue;
                    }
                    createReceivable(accountSetId, parking.getOwnerId(), parking.getId(), fee, BigDecimal.ONE, periodMonth);
                }
            }
        }
    }
    
    /**
     * 检查是否已存在该费用类型的应收账款（用于一次性收费判断）
     */
    private boolean hasReceivableForFeeType(Long accountSetId, Long ownerId, Long parkingId, String feeType) {
        LambdaQueryWrapper<Receivable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Receivable::getAccountSetId, accountSetId)
               .eq(Receivable::getOwnerId, ownerId)
               .eq(Receivable::getFeeType, feeType);
        if (parkingId != null) {
            wrapper.eq(Receivable::getParkingId, parkingId);
        }
        return receivableMapper.selectCount(wrapper) > 0;
    }
    
    private void createReceivable(Long accountSetId, Long ownerId, Long parkingId, 
                                   FeeStandard fee, BigDecimal multiplier, String periodMonth) {
        LambdaQueryWrapper<Receivable> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(Receivable::getAccountSetId, accountSetId)
                    .eq(Receivable::getOwnerId, ownerId)
                    .eq(Receivable::getFeeType, fee.getFeeType())
                    .eq(Receivable::getPeriodMonth, periodMonth);
        if (parkingId != null) {
            checkWrapper.eq(Receivable::getParkingId, parkingId);
        }
        
        if (receivableMapper.selectCount(checkWrapper) > 0) {
            return;
        }
        
        BigDecimal amount = fee.getAmount().multiply(multiplier);
        
        LambdaQueryWrapper<Receivable> cumWrapper = new LambdaQueryWrapper<>();
        cumWrapper.eq(Receivable::getAccountSetId, accountSetId)
                  .eq(Receivable::getOwnerId, ownerId)
                  .eq(Receivable::getFeeType, fee.getFeeType())
                  .orderByDesc(Receivable::getPeriodMonth)
                  .last("LIMIT 1");
        Receivable lastReceivable = receivableMapper.selectOne(cumWrapper);
        BigDecimal cumulative = lastReceivable != null ? 
            lastReceivable.getCumulativeAmount().add(amount) : amount;
        
        Receivable receivable = new Receivable();
        receivable.setAccountSetId(accountSetId);
        receivable.setOwnerId(ownerId);
        receivable.setParkingId(parkingId);
        receivable.setFeeType(fee.getFeeType());
        receivable.setPeriodMonth(periodMonth);
        receivable.setAmount(amount);
        receivable.setPaidAmount(BigDecimal.ZERO);
        receivable.setIsLocked(0);
        receivable.setCumulativeAmount(cumulative);
        
        receivableMapper.insert(receivable);
    }
    
    private String generateBillNo() {
        return "BILL" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
               + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    /**
     * 从系统配置获取默认到期天数
     */
    private int getDefaultDueDays() {
        try {
            String value = configMapper.getValueByKey("default_due_days");
            if (value != null) {
                return Integer.parseInt(value);
            }
        } catch (Exception e) {
            log.warn("读取默认到期天数配置失败，使用默认值15", e);
        }
        return 15;
    }
}
