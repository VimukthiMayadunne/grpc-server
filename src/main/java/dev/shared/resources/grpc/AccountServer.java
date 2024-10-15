package dev.shared.resources.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

public class AccountServer {

    private Server server;
    private final Map<String, BanksService.Account> accountMap = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException, InterruptedException {
        final AccountServer accountServer = new AccountServer();
        accountServer.start();
        accountServer.blockUntilShutdown();
    }

    private void start() throws IOException {
        int port = 50051;
        generateRandomAccounts(); // Generate random accounts at startup

        server = ServerBuilder.forPort(port)
                .addService(new AccountServiceImpl())
                .build()
                .start();

        System.out.println("Server started, listening on " + port);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("Shutting down gRPC server since JVM is shutting down");
            AccountServer.this.stop();
            System.err.println("Server shut down");
        }));
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    // Block until the server is terminated
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    // Generate random accounts
    private void generateRandomAccounts() {
        Random random = new Random();
        for (int i = 1; i <= 10; i++) {
            String id = "ACC" + i;
            String name = "Account " + i;
            double balance = random.nextDouble() * 1000; // Random balance between 0 and 1000

            BanksService.Account account = BanksService.Account.newBuilder()
                    .setId(id)
                    .setName(name)
                    .setBalance(balance)
                    .build();

            accountMap.put(id, account);
        }
    }

    // Implementation of the AccountService
    private class AccountServiceImpl extends AccountServiceGrpc.AccountServiceImplBase {

        @Override
        public void getAllAccounts(BanksService.Empty request, StreamObserver<BanksService.Account> responseObserver) {
            for (BanksService.Account account : accountMap.values()) {
                responseObserver.onNext(account);
            }
            responseObserver.onCompleted();
        }

        @Override
        public void getAccountBalance(BanksService.AccountIdRequest request,
                                      StreamObserver<BanksService.AccountBalanceResponse> responseObserver) {
            String accountId = request.getAccountId();
            BanksService.Account account = accountMap.get(accountId);

            if (account != null) {
                BanksService.AccountBalanceResponse response = BanksService.AccountBalanceResponse.newBuilder()
                        .setAccountId(accountId)
                        .setBalance(account.getBalance())
                        .build();
                responseObserver.onNext(response);
            } else {
                // Account not found; return zero balance or handle as per your requirement
                BanksService.AccountBalanceResponse response = BanksService.AccountBalanceResponse.newBuilder()
                        .setAccountId(accountId)
                        .setBalance(0)
                        .build();
                responseObserver.onNext(response);
            }
            responseObserver.onCompleted();
        }

        @Override
        public void withdrawOrDeposit(BanksService.TransactionRequest request,
                                      StreamObserver<BanksService.TransactionResponse> responseObserver) {
            String accountId = request.getAccountId();
            double amount = request.getAmount();
            BanksService.Account account = accountMap.get(accountId);

            if (account != null) {
                double newBalance = account.getBalance() + amount;
                // Update the account balance
                account = account.toBuilder().setBalance(newBalance).build();
                accountMap.put(accountId, account);

                BanksService.TransactionResponse response = BanksService.TransactionResponse.newBuilder()
                        .setAccountId(accountId)
                        .setNewBalance(newBalance)
                        .build();
                responseObserver.onNext(response);
            } else {
                // Account not found; handle as per your requirement
                // For simplicity, create a new account
                BanksService.Account newAccount = BanksService.Account.newBuilder()
                        .setId(accountId)
                        .setName("New Account")
                        .setBalance(amount)
                        .build();
                accountMap.put(accountId, newAccount);

                BanksService.TransactionResponse response = BanksService.TransactionResponse.newBuilder()
                        .setAccountId(accountId)
                        .setNewBalance(amount)
                        .build();
                responseObserver.onNext(response);
            }
            responseObserver.onCompleted();
        }
    }
}
