//package com.viettel.vocs.microchassis.connection.bibd;
//
//import javax.swing.*;
//import java.awt.event.ActionEvent;
//import java.awt.event.ActionListener;
//import java.awt.event.KeyEvent;
//import java.awt.event.KeyListener;
//import java.util.Random;
//
//public class OldIBD extends JFrame implements ActionListener, KeyListener, Runnable {
//   Thread run;
//   JPanel design = new JPanel();
//   JPanel random = new JPanel();
//   JPanel option = new JPanel();
//   JTextField v_ = new JTextField();
//   JTextField k_ = new JTextField();
//   JTextField r_ = new JTextField();
//   JTextField b_ = new JTextField("0");
//   JTextField seed_ = new JTextField("" + System.currentTimeMillis());
//   JTextField tries_ = new JTextField("1000");
//   ButtonGroup group = new ButtonGroup();
//   JRadioButton row = new JRadioButton("Rows", false);
//   JRadioButton col = new JRadioButton("Columns", true);
//   JButton button = new JButton("Start");
//   JTextArea output = new JTextArea();
//
//   public OldIBD() {
//      super("IBD: Program for Constructing an IBD of size (v,k,r)");
//      Tools.setLookAndFeel();
//      this.setSize(725, 555);
//      this.setDefaultCloseOperation(3);
//      this.setResizable(false);
//      this.setLayout(null);
//      Tools.makePanel(this.design, "Number of", 10, 10, 350, 75);
//      Tools.addPanel(this.design, "Treatments (v)", 10, 20, 100, 20);
//      Tools.addPanel(this.design, this.v_, true, 120, 20, 30, 20);
//      Tools.addPanel(this.design, "Replications (r)", 10, 45, 100, 20);
//      Tools.addPanel(this.design, this.r_, true, 130, 45, 20, 20);
//      Tools.addPanel(this.design, "Plots per block (k)", 185, 20, 120, 20);
//      Tools.addPanel(this.design, this.k_, true, 320, 20, 20, 20);
//      Tools.addPanel(this.design, "Blocks (b)", 185, 45, 100, 20);
//      Tools.addPanel(this.design, this.b_, false, 320, 45, 20, 20);
//      this.v_.addKeyListener(this);
//      this.k_.addKeyListener(this);
//      this.r_.addKeyListener(this);
//      this.add(this.design);
//      Tools.makePanel(this.option, "Print blocks as", 365, 10, 172, 75);
//      Tools.addPanel(this.option, this.row, this.group, true, 5, 20, 100, 20);
//      Tools.addPanel(this.option, this.col, this.group, true, 5, 45, 100, 20);
//      this.add(this.option);
//      Tools.makePanel(this.random, "Use", 542, 10, 173, 75);
//      Tools.addPanel(this.random, "Seed", 10, 20, 75, 20);
//      Tools.addPanel(this.random, this.seed_, false, 53, 20, 110, 20);
//      Tools.addPanel(this.random, "Tries", 10, 45, 75, 20);
//      Tools.addPanel(this.random, this.tries_, false, 53, 45, 110, 20);
//      this.add(this.random);
//      Tools.makeButton(this.button, 335, 90, 50, 20);
//      this.button.addActionListener(this);
//      this.add(this.button);
//      this.add(Tools.makeJScrollPane(this.output, 10, 118, 705, 400));
//      this.setVisible(true);
//   }
//
//   public static void main(String[] var0) {
//      new IBD();
//   }
//
//   @Override
//   public void keyPressed(KeyEvent var1) {
//   }
//
//   @Override
//   public void keyTyped(KeyEvent var1) {
//   }
//
//   @Override
//   public void keyReleased(KeyEvent var1) {
//      int var2 = Utils.parseInt(-1, this.v_.getText());
//      int var3 = Utils.parseInt(-1, this.k_.getText());
//      int var4 = Utils.parseInt(-1, this.r_.getText());
//      if (var2 != -1 && var3 != -1 && var4 != -1 && var3 != 0 && var2 * var4 % var3 == 0) {
//         this.b_.setText("" + var2 * var4 / var3);
//      } else {
//         this.b_.setText("?");
//      }
//   }
//
//   @Override
//   public void actionPerformed(ActionEvent var1) {
//      String var2 = var1.getActionCommand();
//      switch(var2) {
//         case "Start":
//            int var5 = Utils.parseInt(-1, this.v_.getText());
//            int var6 = Utils.parseInt(-1, this.k_.getText());
//            int var7 = Utils.parseInt(-1, this.r_.getText());
//            int var8 = Utils.parseInt(-1, this.b_.getText());
//            if (var5 < 3) {
//               Dialog.error("v should be at least 3.");
//            } else if (var6 < 2) {
//               Dialog.error("k should be at least 2.");
//            } else if (var6 > var5) {
//               Dialog.error("k should not be greater than v.");
//            } else if (var7 < 2) {
//               Dialog.error("r should be at least 2.");
//            } else if (var8 == 0) {
//               Dialog.error("Invalid (v,k,r) combinations.");
//            } else {
//               this.Start();
//            }
//            break;
//         case "Stop":
//            this.Stop();
//            break;
//         default:
//            this.Reset();
//      }
//   }
//
//   void Start() {
//      this.run = new Thread(this);
//      this.run.start();
//      this.button.setText("Stop");
//      this.output.setText(null);
//   }
//
//   void Stop() {
//      this.run = null;
//      this.button.setText("Reset");
//   }
//
//   void Reset() {
//      this.button.setText("Start");
//      this.seed_.setText("" + System.currentTimeMillis());
//      this.tries_.setText("1000");
//      this.output.setText(null);
//   }
//
//   @Override
//   public void run() {
//      Thread var1 = Thread.currentThread();
//      String var2 = null;
//      String var3 = null;
//      String var4 = null;
//      int var5 = 0;
//      int var7 = -1;
//      int var8 = -1;
//      boolean var29 = false;
//      boolean var31 = false;
//      boolean var32 = false;
//      boolean var33 = false;
//      boolean var35 = this.col.isSelected();
//      int var36 = Utils.parseInt(-1, this.v_.getText());
//      int var37 = Utils.parseInt(-1, this.k_.getText());
//      int var38 = Utils.parseInt(-1, this.r_.getText());
//      int var39 = var36 * var38 / var37;
//      long var40 = Utils.parseLong(System.currentTimeMillis(), this.seed_.getText());
//      long var42 = Utils.parseLong(1000L, this.tries_.getText());
//
//      while(!var29) {
//         if (++var5 >= var38) {
//            break;
//         }
//
//         if (var36 * var5 % var37 == 0 && var38 % var5 == 0) {
//            var29 = Dialog.option("Do you want to obtain\na " + var5 + "-resolvable design?");
//         }
//      }
//
//      int var44 = var5 * var36 / var37;
//      long var45 = System.currentTimeMillis();
//      String var47 = "IBD 8.0: Program for Constructing Incomplete Block Designs\n(c) 2017 Design Computing (http://designcomputing.net/)\n\nNote: Best "
//         + (var5 < var38 ? var5 + "-resolvable " : "")
//         + "IBD for v="
//         + var36
//         + ", k="
//         + var37
//         + ", r="
//         + var38
//         + " and b="
//         + var39
//         + "\n\nTry\titer\tE\tE/U\tConcurrences\n";
//      this.output.setText(var47);
//      double var27;
//      if (var36 <= var39) {
//         var27 = Bound.jarrett(var38, var37, var39, false);
//      } else {
//         var27 = ((double)var36 - 1.0) / ((double)(var36 - var39) + ((double)var39 - 1.0) / Bound.jarrett(var37, var38, var36, false));
//      }
//
//      if (var5 == 1) {
//         var27 = Math.min(var27, Bound.jarrett(var38, var37, var44, true));
//      }
//
//      if (var5 == 1 && var38 == 2 && var37 <= var44) {
//         var31 = true;
//         var39 = var44;
//         var36 = var44;
//         var38 = var37;
//      } else if (var36 > var39) {
//         var32 = true;
//         var33 = var5 < var38;
//         int var10 = var36;
//         var36 = var39;
//         var39 = var10;
//         int var65 = var38;
//         var38 = var37;
//         var37 = var65;
//         var44 = var10;
//      }
//
//      int var48 = var38 * (var37 - 1) / (var36 - 1);
//      int var49 = var38 * (var37 - 1) - var48 * (var36 - 1);
//      long var50 = (long)(var49 * var36 * Math.max(0, 2 * var49 - var36));
//      if (var48 == 0) {
//         var50 = Math.max(var50, (long)(var38 * var36 * (var37 - 1) * (var37 - 2)));
//      }
//
//      long var52 = (long)(var36 * var49 / 2);
//      long var54 = var50 / 6L;
//      double var25 = 0.0;
//      double var23 = 0.0;
//      long var17 = Long.MAX_VALUE;
//      long var13 = Long.MAX_VALUE;
//      long var15 = Long.MAX_VALUE;
//
//      for(int var59 = 1; (long)var59 <= var42 && var23 / var27 < 0.99995 && this.run == var1; ++var59) {
//         int var6 = 0;
//         Random var56;
//         Layout var57;
//         if (var33) {
//            var57 = new Layout(var39, var36, var38);
//            var57.construct();
//            var57.dualized();
//            var57.colRand(var56 = new Random(var40));
//         } else {
//            var57 = new Layout(var36, var39, var37);
//            var57.construct();
//            var57.rowRand(var56 = new Random(var40));
//            var57.colRand(var56, var44);
//         }
//
//         Matrix var58 = var57.concurrences();
//         if (var48 > 0) {
//            for(int var60 = 0; var60 < var36; ++var60) {
//               for(int var61 = var60 + 1; var61 < var36; ++var61) {
//                  var58.set(var60, var61, var58.get(var60, var61) - (double)var48);
//               }
//            }
//         }
//
//         long var11 = (long)var58.f();
//
//         long var19;
//         do {
//            var29 = false;
//
//            for(int var74 = 0; var74 < var39 - 1; ++var74) {
//               for(int var77 = var74 + 1; var77 < var39; ++var77) {
//                  if (var11 > var52 && var74 / var44 == var77 / var44) {
//                     while(true) {
//                        boolean var30 = false;
//                        long var21 = 0L;
//
//                        for(int var62 = 0; var62 < var37; ++var62) {
//                           for(int var63 = 0; var63 < var37; ++var63) {
//                              int var9;
//                              int var66;
//                              if ((var9 = var57.get(var74, var62)) != (var66 = var57.get(var77, var63))
//                                 && var9 != var66
//                                 && (!var33 || var62 == var63)
//                                 && var57.notin(var9, var77)
//                                 && var57.notin(var66, var74)
//                                 && (var19 = var57.delta(var58, var74, var62, var77, var63)) < var21) {
//                                 var21 = var19;
//                                 var7 = var62;
//                                 var8 = var63;
//                              }
//                           }
//                        }
//
//                        if (var21 < 0L) {
//                           var30 = true;
//                           var29 = true;
//                           ++var6;
//                           var11 += 2L * var21;
//                           var58.update(var57, var74, var7, var77, var8);
//                           var57.swap(var74, var7, var77, var8);
//                        }
//
//                        if (!var30) {
//                           break;
//                        }
//                     }
//                  }
//               }
//            }
//         } while(var29 && var11 > var52);
//
//         boolean var34 = var11 == var52;
//         if (var34) {
//            var15 = (long)var58.f3();
//         }
//
//         if (var34) {
//            do {
//               var29 = false;
//
//               for(int var75 = 0; var75 < var39 - 1; ++var75) {
//                  for(int var78 = var75 + 1; var78 < var39; ++var78) {
//                     if (var15 > var54 && var75 / var44 == var78 / var44) {
//                        while(true) {
//                           boolean var72 = false;
//                           long var68 = 0L;
//
//                           for(int var80 = 0; var80 < var37; ++var80) {
//                              for(int var81 = 0; var81 < var37; ++var81) {
//                                 int var64;
//                                 int var67;
//                                 if ((var64 = var57.get(var75, var80)) != (var67 = var57.get(var78, var81))
//                                    && (!var33 || var80 == var81)
//                                    && var64 != var67
//                                    && var57.notin(var64, var78)
//                                    && var57.notin(var67, var75)
//                                    && var57.delta(var58, var75, var80, var78, var81) == 0L
//                                    && (var19 = var57.gamma(var58, var75, var80, var78, var81)) < var68) {
//                                    var68 = var19;
//                                    var7 = var80;
//                                    var8 = var81;
//                                 }
//                              }
//                           }
//
//                           if (var68 < 0L) {
//                              var72 = true;
//                              var29 = true;
//                              ++var6;
//                              var15 += var68;
//                              var58.update(var57, var75, var7, var78, var8);
//                              var57.swap(var75, var7, var78, var8);
//                           }
//
//                           if (!var72) {
//                              break;
//                           }
//                        }
//                     }
//                  }
//               }
//            } while(var29 && var15 > var54);
//         }
//
//         if (var48 > 0) {
//            for(int var76 = 0; var76 < var36; ++var76) {
//               for(int var79 = var76 + 1; var79 < var36; ++var79) {
//                  var58.set(var76, var79, var58.get(var76, var79) + (double)var48);
//               }
//            }
//         }
//
//         if (var11 < var13 || var34 && var15 <= var17) {
//            var23 = var58.efficiency((double)var38, var37);
//            if (var23 > 1.0E-5 && var31) {
//               var23 = ((double)(var37 * var44) - 1.0) / ((double)(var37 * var44) - 2.0 * (double)var44 + 1.0 + 4.0 * ((double)var44 - 1.0) / var23);
//            } else if (var23 > 1.0E-5 && var32) {
//               var23 = ((double)var39 - 1.0) / ((double)(var39 - var36) + ((double)var36 - 1.0) / var23);
//            }
//
//            var23 = Utils.round(var23);
//            if (var11 < var13 || var34 && var15 < var17 || var34 && var15 == var17 && var23 > var25) {
//               var25 = var23;
//               var13 = var11;
//               var17 = var15;
//               if (var31) {
//                  var57.expanded(var56);
//               } else if (var32) {
//                  var57.dualized();
//               }
//
//               var57.rowRand(var56);
//               var58 = var57.concurrences();
//               var2 = var59 + "\t" + var6 + "\t" + Utils.toString(var23) + "\t" + Utils.toString(var23 / var27) + "\t" + var58.frequencies() + "\n";
//               this.output.append(var2);
//               var4 = "\nPlan for try "
//                  + var59
//                  + " using seed "
//                  + var40
//                  + (var35 ? " (blocks are columns):\n\n" + var57.cols(var5) : " (blocks are rows):\n\n" + var57.rows(var5))
//                  + "Note: IBD used "
//                  + (double)(System.currentTimeMillis() - var45) / 1000.0
//                  + " seconds.";
//               var3 = var57.form(var5, "Block\tPlot\tTreat\n");
//            }
//         }
//
//         var40 = (long)var56.nextInt();
//      }
//
//      this.output.append(var4 + "\nNote: IBD.htm has been created.");
//      this.Stop();
//   }
//}
