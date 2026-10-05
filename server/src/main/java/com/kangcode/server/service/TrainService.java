package com.kangcode.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kangcode.pojo.vo.TrainVO;

/**
 * 车次服务
 */
public interface TrainService {

    IPage<TrainVO> pageTrain(int page, int size, String keyword);

    TrainVO getTrainDetail(Long trainId);
}
