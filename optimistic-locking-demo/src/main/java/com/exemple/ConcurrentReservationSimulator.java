package com.exemple;

import com.exemple.model.Reservation;
import com.exemple.model.Salle;
import com.exemple.model.Utilisateur;
import com.exemple.service.ReservationService;
import com.exemple.service.ReservationServiceImpl;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.OptimisticLockException;
import javax.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConcurrentReservationSimulator {

    private static final EntityManagerFactory emf =
            Persistence.createEntityManagerFactory("optimistic-locking-demo");

    private static final ReservationService reservationService = new ReservationServiceImpl(emf);

    public static void main(String[] args) throws InterruptedException {
        // Initialisation des données
        initData();

        // Simulation d'un conflit de réservation concurrent sans retry
        System.out.println("\n=== Simulation d'un conflit sans retry ===");
        simulateConcurrentReservationConflict();

        // Réinitialisation des données
        initData();

        // Simulation d'un conflit avec retry
        System.out.println("\n=== Simulation d'un conflit avec retry ===");
        simulateConcurrentReservationConflictWithRetry();

        // Fermeture de l'EntityManagerFactory
        emf.close();
    }

    // ---------------------- Initialisation des données ----------------------
    private static void initData() {
        Utilisateur utilisateur1 = new Utilisateur("Dupont", "Jean", "jean.dupont@example.com");
        Utilisateur utilisateur2 = new Utilisateur("Martin", "Sophie", "sophie.martin@example.com");

        Salle salle = new Salle("Salle A101", 30);
        salle.setDescription("Salle de réunion équipée d'un projecteur");

        try {
            EntityManager em = emf.createEntityManager();
            em.getTransaction().begin();

            em.persist(utilisateur1);
            em.persist(utilisateur2);
            em.persist(salle);

            Reservation reservation = new Reservation(
                    LocalDateTime.now().plusDays(1).withHour(10).withMinute(0),
                    LocalDateTime.now().plusDays(1).withHour(12).withMinute(0),
                    "Réunion d'équipe"
            );
            reservation.setUtilisateur(utilisateur1);
            reservation.setSalle(salle);

            em.persist(reservation);

            em.getTransaction().commit();
            em.close();

            System.out.println("Données initialisées avec succès !");
            System.out.println("Réservation créée : " + reservation);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------------------- Simulation sans retry ----------------------
    private static void simulateConcurrentReservationConflict() throws InterruptedException {
        Optional<Reservation> reservationOpt = reservationService.findById(1L);
        if (!reservationOpt.isPresent()) {
            System.out.println("Réservation non trouvée !");
            return;
        }

        Reservation reservation = reservationOpt.get();
        System.out.println("Réservation récupérée : " + reservation);

        CountDownLatch latch = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            try {
                latch.await();
                Reservation r1 = reservationService.findById(1L).get();
                System.out.println("Thread 1 : version = " + r1.getVersion());
                Thread.sleep(1000);
                r1.setMotif("Réunion d'équipe modifiée par Thread 1");
                try {
                    reservationService.update(r1);
                    System.out.println("Thread 1 : mise à jour OK !");
                } catch (OptimisticLockException e) {
                    System.out.println("Thread 1 : Conflit détecté !");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                latch.await();
                Reservation r2 = reservationService.findById(1L).get();
                System.out.println("Thread 2 : version = " + r2.getVersion());
                r2.setDateDebut(r2.getDateDebut().plusHours(1));
                r2.setDateFin(r2.getDateFin().plusHours(1));
                try {
                    reservationService.update(r2);
                    System.out.println("Thread 2 : mise à jour OK !");
                } catch (OptimisticLockException e) {
                    System.out.println("Thread 2 : Conflit détecté !");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        thread1.start();
        thread2.start();
        latch.countDown();

        thread1.join();
        thread2.join();

        Optional<Reservation> finalReservationOpt = reservationService.findById(1L);
        finalReservationOpt.ifPresent(r -> {
            System.out.println("\nÉtat final de la réservation :");
            System.out.println("ID : " + r.getId());
            System.out.println("Motif : " + r.getMotif());
            System.out.println("Date début : " + r.getDateDebut());
            System.out.println("Date fin : " + r.getDateFin());
            System.out.println("Version : " + r.getVersion());
        });
    }

    // ---------------------- Simulation avec retry ----------------------
    private static void simulateConcurrentReservationConflictWithRetry() throws InterruptedException {
        // Handler de retry
        OptimisticLockingRetryHandler retryHandler = new OptimisticLockingRetryHandler(reservationService, 3);

        CountDownLatch latch = new CountDownLatch(1);

        Thread thread1 = new Thread(() -> {
            try {
                latch.await();
                retryHandler.executeWithRetry(1L, r -> {
                    System.out.println("Thread 1 : Modification du motif");
                    r.setMotif("Réunion d'équipe modifiée par Thread 1");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(ConcurrentReservationSimulator.class.getName()).log(Level.SEVERE, null, ex);
                    }
                });
            } catch (Exception e) {
                System.out.println("Thread 1 : Exception finale : " + e.getMessage());
            }
        });

        Thread thread2 = new Thread(() -> {
            try {
                latch.await();
                retryHandler.executeWithRetry(1L, r -> {
                    System.out.println("Thread 2 : Modification des dates");
                    r.setDateDebut(r.getDateDebut().plusHours(1));
                    r.setDateFin(r.getDateFin().plusHours(1));
                });
            } catch (Exception e) {
                System.out.println("Thread 2 : Exception finale : " + e.getMessage());
            }
        });

        thread1.start();
        thread2.start();
        latch.countDown();

        thread1.join();
        thread2.join();

        Optional<Reservation> finalReservationOpt = reservationService.findById(1L);
        finalReservationOpt.ifPresent(r -> {
            System.out.println("\nÉtat final de la réservation avec retry :");
            System.out.println("ID : " + r.getId());
            System.out.println("Motif : " + r.getMotif());
            System.out.println("Date début : " + r.getDateDebut());
            System.out.println("Date fin : " + r.getDateFin());
            System.out.println("Version : " + r.getVersion());
        });
    }

    // ---------------------- Handler simple pour retry ----------------------
    static class OptimisticLockingRetryHandler {
        private final ReservationService reservationService;
        private final int maxRetries;

        public OptimisticLockingRetryHandler(ReservationService service, int maxRetries) {
            this.reservationService = service;
            this.maxRetries = maxRetries;
        }

        public void executeWithRetry(long reservationId, Consumer<Reservation> action) throws Exception {
            int attempt = 0;
            while (true) {
                attempt++;
                try {
                    Reservation r = reservationService.findById(reservationId)
                            .orElseThrow(() -> new Exception("Réservation non trouvée"));
                    action.accept(r);
                    reservationService.update(r);
                    return; // succès
                } catch (OptimisticLockException e) {
                    if (attempt >= maxRetries) {
                        throw new Exception("Échec après " + maxRetries + " tentatives", e);
                    }
                    System.out.println("Retry " + attempt + " dû à conflit optimiste");
                    Thread.sleep(500); // pause avant retry
                }
            }
        }
    }
}
