package com.example.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer // 啟用 Config Server，讓應用程式提供集中式設定檔 API。
/**
 *   例如：
 *
 *   Account → http://configserver:8071/account/default
 *   Loan    → http://configserver:8071/loan/default
 *   Card    → http://configserver:8071/card/default
 *
 *   Config Server 會從 Git 或本機資料夾讀取：
 *
 *   configyml/account.yml
 *   configyml/loan.yml
 *   configyml/card.yml
 */
public class ConfigserverApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigserverApplication.class, args);
    }
}
