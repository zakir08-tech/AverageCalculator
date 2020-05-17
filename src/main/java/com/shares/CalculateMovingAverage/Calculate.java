package com.shares.CalculateMovingAverage;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.codoid.products.exception.FilloException;
import com.codoid.products.fillo.Connection;
import com.codoid.products.fillo.Fillo;
import com.codoid.products.fillo.Recordset;

public class Calculate {
    public static DecimalFormat df = new DecimalFormat("0.00");
    public static TreeMap < Date, HashMap < String, String >> shareDetailsMaster = new TreeMap < Date, HashMap < String, String >> ();
    public static HashMap < String, String > shareDetails = new HashMap < String, String > ();

    public static TreeMap < Integer, HashMap < String, Double >> movingAvgMaster = new TreeMap < Integer, HashMap < String, Double >> ();
    public static HashMap < String, Double > movingAverage = new HashMap < String, Double > ();

    public static TreeMap < Integer, HashMap < String, String >> itemsMaster = new TreeMap < Integer, HashMap < String, String >> ();
    public static HashMap < String, String > itemLsit = new HashMap < String, String > ();
    
    public static TreeMap < Integer, String> holdLastShare = new TreeMap < Integer, String>();  
    
    public static String sysDir = System.getProperty("user.dir");
    public static Integer noOfDays;
    public static Integer extraNoOfDays;
    public static String startDate;
    public static String shareName;	
    public static String calcPath = sysDir + "\\MovingAverageCalculator\\Calculate.xlsm";
    public static String shareListFilePath = sysDir + "\\MovingAverageCalculator\\SharesDatabase.csv";
    public static String strSelectQuerry = "Select * from  Calculate";
    public static BufferedReader csvBuffer;
    public static SimpleDateFormat formatter = new SimpleDateFormat("dd-MMM-yyyy");
    public static String line = "";
    public static String splitBy = ",";
    public static Date startNewDate;
    public static Date startListDate;
    public static String readAllTabData = "";
    
    public static void main(String[] args) {
        boolean firstDateFnd;
        Integer listCnt = 0;
        Integer panelCnt = 0;
        String prevShareName = "Test";
        boolean recFnd = false;
        boolean readList = false;
        
        try {
            Fillo fillo = new Fillo();
            Connection connection = fillo.getConnection(calcPath);
            Recordset recordset = null;
            recordset = connection.executeQuery(strSelectQuerry);
            System.out.println("reading items to calculate moving average...");
            while (recordset.next()) {
            	if(!recordset.getField("Share Company Name").isEmpty()) {
            		listCnt++;
                    itemLsit.put("ShareName", recordset.getField("Share Company Name"));
                    itemLsit.put("AvgerageDays", recordset.getField("Moving Average Of Days"));
                    itemLsit.put("StartDate", recordset.getField("Start Date"));
                    itemsMaster.put(listCnt, itemLsit);
                    itemLsit = new HashMap < String, String > ();
            	}   
            }
            connection.close();
        } catch (FilloException e) {
            System.out.println(e.getMessage());
        }

        try {
        	readOnlyShareNameFromItemList();
            for (Entry < Integer, HashMap < String, String >> listEntry: itemsMaster.entrySet()) {
            	panelCnt++;
            	extraNoOfDays = 0;
                firstDateFnd = false;
                movingAvgMaster = new TreeMap < Integer, HashMap < String, Double >> ();
 
                HashMap<String, String> childListMap = listEntry.getValue();
                shareName = childListMap.get("ShareName");
                noOfDays = Integer.valueOf(childListMap.get("AvgerageDays"));
                startDate = childListMap.get("StartDate");
                startNewDate = formatter.parse(startDate);
                
                if (!shareName.contentEquals(prevShareName)) {
                	shareDetailsMaster = new TreeMap < Date, HashMap < String, String >> ();
                	csvBuffer = new BufferedReader(new FileReader(shareListFilePath));
                	System.out.println("start calculating moving average for " +shareName);
                    readShareDataFromList();
                    readList = true;
                    prevShareName = shareName;
                }
                
                if(shareDetailsMaster.size()==0)
                	System.out.println("no data found for " +shareName+ " in the database!");
                	
                for (Entry < Date, HashMap < String, String >> entry: shareDetailsMaster.entrySet()) {
                    if (firstDateFnd == true) {
                    	recFnd = true;
                    	 getAvgOfNextNoOfDays(noOfDays, entry.getKey());
                    }
                    
                    if (firstDateFnd == false) {
                    	if (entry.getKey().equals(startNewDate) || entry.getKey().after(startNewDate)) {
                            getAvgOfNextNoOfDays(noOfDays, entry.getKey());
                            recFnd = true;
                            firstDateFnd = true;
                        }else
                        	extraNoOfDays++;
                    }
                }
                
                readAllTabData = readAllTabData + createMovingAverageTable(panelCnt);
                
                if ((recFnd == true && holdLastShare.size()==panelCnt) || 
                		(recFnd == true && !holdLastShare.get(panelCnt+1).contentEquals(prevShareName))) {
            		generateMovingAverageReport();
                	readAllTabData = "";
                	recFnd = false;
            	}
                csvBuffer.close();
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    public static void getAvgOfNextNoOfDays(int dayDiff, Date getStartDate) {
        double openPrice = 0;
        double highPrice = 0;
        double lowPrice = 0;
        double closePrice = 0;
        double totalTradedQuantity = 0;
        int dayCount = 0;
        boolean startRow = false;
        String getNewTxt = "";
        
        for (Entry < Date, HashMap < String, String >> entry: shareDetailsMaster.entrySet()) {
        	
            if (startRow == true)
                getStartDate = entry.getKey();

            if (entry.getKey().equals(getStartDate)) {
            	dayCount++;
                startRow = true;
                Map < String, String > childMap = entry.getValue();
                
                openPrice = openPrice + Double.valueOf(childMap.get("OpenPrice"));
                highPrice = highPrice + Double.valueOf(childMap.get("HighPrice"));
                lowPrice = lowPrice + Double.valueOf(childMap.get("LowPrice"));
                closePrice = closePrice + Double.valueOf(childMap.get("ClosePrice"));
                totalTradedQuantity = totalTradedQuantity + Double.valueOf(childMap.get("TotalTradedQuantity"));
            }
            if (dayCount == noOfDays)
                break;
        }

        double openPriceAvg = Math.round((openPrice / dayDiff) * 100.0) / 100.0;
        double highPriceAvg = Math.round((highPrice / dayDiff) * 100.0) / 100.0;
        double lowPriceAvg = Math.round((lowPrice / dayDiff) * 100.0) / 100.0;
        double closePriceAvg = Math.round((closePrice / dayDiff) * 100.0) / 100.0;
        double totalTradeQtyAvg = (double) Math.round((totalTradedQuantity / dayDiff) * 100.0) / 100.0;

        if (String.format("%.2f", totalTradeQtyAvg).contains(".00"))
            getNewTxt = String.format("%.0f", totalTradeQtyAvg);
        else
            getNewTxt = String.format("%.1f", totalTradeQtyAvg);

        movingAverage.put("OpenPrice", openPriceAvg);
        movingAverage.put("HighPrice", highPriceAvg);
        movingAverage.put("LowPrice", lowPriceAvg);
        movingAverage.put("ClosePrice", closePriceAvg);
        movingAverage.put("TotalTradedQuantity", totalTradeQtyAvg);

        Integer getMapSize = movingAvgMaster.size();

        movingAvgMaster.put(getMapSize + 1, movingAverage);
        movingAverage = new HashMap < String, Double > ();
    }

    public static void generateMovingAverageReport() {
        String htmlData1 = "<!DOCTYPE html>\r\n" + "<html lang=\"en\">\r\n" +
            "  <head>\r\n" +
            "     <title>Moving Average Calculator</title>\r\n" +
            "     <meta charset=\"utf-8\">\r\n" +
            "     <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n" +
            "     <link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.4.1/css/bootstrap.min.css\">\r\n" +
            "     <script src=\"https://ajax.googleapis.com/ajax/libs/jquery/3.4.1/jquery.min.js\"></script>\r\n" +
            "     <script src=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.4.1/js/bootstrap.min.js\"></script>\r\n" +
            "     <style style=\"text/css\">\r\n" +
            "         .hoverTable{\r\n" +
            "         width:100%; \r\n" +
            "         border-collapse:collapse; \r\n" +
            "         }\r\n" + "         /* Define the hover highlight color for the table row */\r\n" +
            "         .hoverTable tr:hover {\r\n" +
            "         background-color: #ffff99;\r\n" +
            "         }\r\n" +
            "		 \r\n" +
            "         #myBtn {\r\n" +
            "         display: none;\r\n" +
            "         position: fixed;\r\n" +
            "         bottom: 70px;\r\n" +
            "         right: 90px;\r\n" +
            "         z-index: 99;\r\n" +
            "         font-size: 20px;\r\n" +
            "         border: none;\r\n" +
            "         outline: none;\r\n" +
            "         background-color: red;\r\n" +
            "         color: white;\r\n" +
            "         cursor: pointer;\r\n" +
            "         padding: 5px;\r\n" +
            "         border-radius: 5px;\r\n" +
            "         }\r\n" +
            "		 \r\n" +
            "         #myBtn:hover {\r\n" +
            "         background-color: #428bca;\r\n" +
            "         }\r\n" +
            "     </style>\r\n" +
            "  </head>\r\n" +
            "  <body>\r\n" +
            "      <button class='btn btn-default' onclick=\"topFunction()\" id=\"myBtn\" title=\"Scroll to top\">\r\n" +
            "      <span class='glyphicon glyphicon-triangle-top'></span>\r\n" +
            "      </button>\r\n" +
            "     <div class=\"container\">\r\n" +
            "     <h4 style=\"font-family:consolas; color:red;text-align:center;\"><b>Moving Average Calculator</b></h4>\r\n" +
            "     <div class=\"panel-group\">\r\n" +
            "        <div class=\"panel panel-primary\">\r\n" +
            "           <div class=\"panel-heading\"><b><font color=\"yellow\";>" +shareName+ "</font></b>: Moving Average Report</div>\r\n" +
            "           <div class=\"panel-body\">\r\n" +
            "              <div class=\"panel-group\" id=\"accordion\">\r\n";
        
        String htmlData2 = "              </div>\r\n" +
            "           </div>\r\n" +
            "        </div>\r\n" +
            "     </div>\r\n" +
            "      <script>\r\n" +
            "         //Get the button\r\n" +
            "         var mybutton = document.getElementById(\"myBtn\");\r\n" +
            "         \r\n" +
            "         // When the user scrolls down 20px from the top of the document, show the button\r\n" +
            "         window.onscroll = function() {scrollFunction()};\r\n" +
            "         \r\n" +
            "         function scrollFunction() {\r\n" +
            "           if (document.body.scrollTop > 20 || document.documentElement.scrollTop > 20) {\r\n" +
            "             mybutton.style.display = \"block\";\r\n" +
            "           } else {\r\n" +
            "             mybutton.style.display = \"none\";\r\n" +
            "           }\r\n" +
            "         }\r\n" +
            "         \r\n" +
            "         // When the user clicks on the button, scroll to the top of the document\r\n" +
            "         function topFunction() {\r\n" +
            "           document.body.scrollTop = 0;\r\n" +
            "           document.documentElement.scrollTop = 0;\r\n" +
            "         }\r\n" +
            "      </script>\r\n" +
            "  </body>\r\n" +
            "</html>";

        String htmlPage = htmlData1 + readAllTabData + htmlData2;
        System.out.println("start generating report....");
        saveMovingAverageReport(htmlPage);
        System.out.println("moving average calculation is over!");
    }

    public static void saveMovingAverageReport(String htmlDocument) {
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("dd-MMMM-yyyy hh:mm:ss a");
        String strDate = formatter.format(date).toLowerCase();
        String UniqueID = strDate.replaceAll(":", "_");

        String tempDir = System.getProperty("java.io.tmpdir");
        File file = new File(tempDir +shareName+ "_MovingAverageReport_" + UniqueID + ".html");
        FileWriter fw = null;

        try {
            fw = new FileWriter(file.getAbsoluteFile());
            BufferedWriter bw = new BufferedWriter(fw);
            bw.write(htmlDocument);
            bw.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    public static String getMovingAverage(Integer recNo, String keyName) {
        String getTheAverage = null;
        boolean recFnd = false;

        for (Entry < Integer, HashMap < String, Double >> entry: movingAvgMaster.entrySet()) {
            if (entry.getKey().equals(recNo)) {
                Map < String, Double > childMap = entry.getValue();
                for (Entry < String, Double > entry2: childMap.entrySet()) {
                    if (entry2.getKey().toString().contentEquals(keyName)) {
                        getTheAverage = entry2.getValue().toString();
                        recFnd = true;
                        break;
                    }
                }
            }
            if (recFnd == true)
                break;
        }
        return getTheAverage;
    }

    public static void readShareDataFromList() {
        // parsing a CSV file into BufferedReader class constructor
        try {
            while ((line = csvBuffer.readLine()) != null) // returns a Boolean value
            {
                String[] employee = line.split(splitBy); // use comma as separator
                if (employee[0].contentEquals(shareName)) {

                    startListDate = formatter.parse(employee[10]);

                    shareDetails.put("OpenPrice", employee[2]);
                    shareDetails.put("HighPrice", employee[3]);
                    shareDetails.put("LowPrice", employee[4]);
                    shareDetails.put("ClosePrice", employee[5]);
                    shareDetails.put("TotalTradedQuantity", employee[8]);
                    shareDetails.put("TimeStamp", employee[10]);

                    shareDetailsMaster.put(startListDate, shareDetails);
                    shareDetails = new HashMap < String, String > ();
                }

            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (ParseException e) {
            System.out.println(e.getMessage());
        }
    }

    public static String createMovingAverageTable(Integer panelNo) {
        String htmlTable = "";
        String htmlTableTxt = "";
        String timeStampTxt = "";
        String openPriceTxt = "";
        String highPriceTxt = "";
        String lowPriceTxt = "";
        String closePriceTxt = "";
        String totalTradedQuantityTxt = "";
        int recCnt = 1;
        int recCntx=1;
        String getNewTxt = "";
        int noOfDaysx = noOfDays;
        int extraNoOfDaysx = extraNoOfDays;
        double getOpenPriceNew=0;
        double getPrevOpenPrice=0;
        
        String htmlPanelBody = "                 <div class=\"panel panel-default\">\r\n" +
            "                    <div class=\"panel-heading\">\r\n" +
            "                       <h4 class=\"panel-title\">\r\n" +
            "                          <a data-toggle=\"collapse\" data-parent=\"#accordion\" href=\"#collapse"+panelNo+"\"><font style=\"color:red\"><b>" + noOfDays + "</b></font> day Moving Average</a>\r\n" +
            "                       </h4>\r\n" +
            "                    </div>\r\n" +
            "                    <div id=\"collapse"+panelNo+"\" class=\"panel-collapse collapse\">\r\n" +
            "                       <div class=\"panel-body\">\r\n" +
            "                          <table class=\"hoverTable\">\r\n" +
            "                             <thead>\r\n" +
            "								<tr>\r\n" + 
            "                                   <th style=\"font-family:consolas; color:red;text-align:center;\"><i>Actual Data:</i></th>\r\n" + 
            "                                   <th></th>\r\n" + 
            "                                   <th></th>\r\n" + 
            "                                   <th></th>\r\n" + 
            "                                   <th></th>\r\n" + 
            "                                   <th></th>\r\n" + 
            "                                   <th></th>\r\n" + 
            "                                   <th style=\"font-family:consolas; color:#428bca;text-align:center;\"><i>Moving Average:</i></th>\r\n" + 
            "                                   <th></th>\r\n" + 
            "                                   <th></th>\r\n" + 
            "                                   <th></th>\r\n" + 
            "                                   <th></th>\r\n" + 
            "                                </tr>\r\n" + 
            "                                <tr>\r\n" +
            "                                   <th style=\"color:#428bca\">Date</th>\r\n" +
            "                                   <th style=\"color:#428bca\">Open Price</th>\r\n" +
            "                                   <th style=\"color:#428bca\">High Price</th>\r\n" +
            "                                   <th style=\"color:#428bca\">Low Price</th>\r\n" +
            "                                   <th style=\"color:#428bca\">Close Price</th>\r\n" +
            "                                   <th style=\"color:#428bca\">Total Traded Quantity</th>\r\n" +
            "                                   <th></th>\r\n" +
            "                                   <th style=\"color:red\">Open Price</th>\r\n" +
            "                                   <th style=\"color:red\">High Price</th>\r\n" +
            "                                   <th style=\"color:red\">Low Price</th>\r\n" +
            "                                   <th style=\"color:red\">Close Price</th>\r\n" +
            "                                   <th style=\"color:red\">Total Traded Quantity</th>\r\n" +
            "                                </tr>\r\n" + "                             </thead>\r\n" +
            "                             <tbody>\r\n";

        for (Entry < Date, HashMap < String, String >> entry: shareDetailsMaster.entrySet()) {
            Map < String, String > childMap = entry.getValue();
            timeStampTxt = childMap.get("TimeStamp");
            openPriceTxt = childMap.get("OpenPrice");
            highPriceTxt = childMap.get("HighPrice");
            lowPriceTxt = childMap.get("LowPrice");
            closePriceTxt = childMap.get("ClosePrice");
            totalTradedQuantityTxt = childMap.get("TotalTradedQuantity");
            
            if(recCnt == extraNoOfDaysx+1) {
            	htmlTableTxt = "                                <tr style=\"background-color:orange\">\r\n" +
                        "                                   <td>" + timeStampTxt + "</td>\r\n" +
                        "                                   <td>" + openPriceTxt + "</td>\r\n" +
                        "                                   <td>" + highPriceTxt + "</td>\r\n" +
                        "                                   <td>" + lowPriceTxt + "</td>\r\n" +
                        "                                   <td>" + closePriceTxt + "</td>\r\n" +
                        "                                   <td>" + totalTradedQuantityTxt + "</td>\r\n" +
                        "                                   <td></td>\r\n";
            }else {
            	htmlTableTxt = "                                <tr>\r\n" +
                        "                                   <td>" + timeStampTxt + "</td>\r\n" +
                        "                                   <td>" + openPriceTxt + "</td>\r\n" +
                        "                                   <td>" + highPriceTxt + "</td>\r\n" +
                        "                                   <td>" + lowPriceTxt + "</td>\r\n" +
                        "                                   <td>" + closePriceTxt + "</td>\r\n" +
                        "                                   <td>" + totalTradedQuantityTxt + "</td>\r\n" +
                        "                                   <td></td>\r\n";
            }
            
            if (recCnt > (noOfDaysx-1)+extraNoOfDaysx) {
            	if(recCntx>=1) {
            		getOpenPriceNew = Double.valueOf(getMovingAverage(recCntx , "OpenPrice"));
            		if(recCntx !=1)
            			getPrevOpenPrice = Double.valueOf(getMovingAverage(recCntx-1 , "OpenPrice"));
            	}
            	
            	if(getOpenPriceNew <= getPrevOpenPrice) {
            		htmlTableTxt = htmlTableTxt + "                                   <td style=\"background-color:pink\">" + getMovingAverage(recCntx , "OpenPrice") + "</td>\r\n" +
                            "                                   <td style=\"background-color:pink\">" + getMovingAverage(recCntx , "HighPrice") + "</td>\r\n" +
                            "                                   <td style=\"background-color:pink\">" + getMovingAverage(recCntx , "LowPrice") + "</td>\r\n" +
                            "                                   <td style=\"background-color:pink\">" + getMovingAverage(recCntx , "ClosePrice") + "</td>\r\n";

            	}else
            	{
            		htmlTableTxt = htmlTableTxt + "                                   <td>" + getMovingAverage(recCntx , "OpenPrice") + "</td>\r\n" +
                            "                                   <td>" + getMovingAverage(recCntx , "HighPrice") + "</td>\r\n" +
                            "                                   <td>" + getMovingAverage(recCntx , "LowPrice") + "</td>\r\n" +
                            "                                   <td>" + getMovingAverage(recCntx , "ClosePrice") + "</td>\r\n";
            	}
                
                String getTotalTrdQty = getMovingAverage(recCntx , "TotalTradedQuantity");

                if (String.format("%.2f", Double.parseDouble(getTotalTrdQty)).contains(".00"))
                    getNewTxt = String.format("%.0f", Double.parseDouble(getTotalTrdQty));
                else
                    getNewTxt = String.format("%.1f", Double.parseDouble(getTotalTrdQty));
                
                if(getOpenPriceNew <= getPrevOpenPrice) {
                	htmlTableTxt = htmlTableTxt + "                                   <td style=\"background-color:pink\">" + getNewTxt + "</td>\r\n" +
                            "                                </tr>\r\n";

                }else
                {
                	htmlTableTxt = htmlTableTxt + "                                   <td>" + getNewTxt + "</td>\r\n" +
                            "                                </tr>\r\n";

                }
                                recCntx++;
            }
			else {
                htmlTableTxt = htmlTableTxt + "                                   <td>-</td>\r\n" +
                    "                                   <td>-</td>\r\n" +
                    "                                   <td>-</td>\r\n" +
                    "                                   <td>-</td>\r\n" +
                    "                                   <td>-</td>\r\n";
            }

            htmlTable = htmlTable + htmlTableTxt;
            htmlTableTxt = "";
            recCnt++;
        }

        htmlTable = htmlTable + "                             </tbody>\r\n" +
            "                          </table>\r\n" +
            "                       </div>\r\n" +
            "                    </div>\r\n" +
            "                 </div>\r\n";
        
        return htmlPanelBody + htmlTable;
    }
    
    public static void readOnlyShareNameFromItemList() {
    	Integer shareListcnt = 0;
    	for (Entry < Integer, HashMap < String, String >> listEntry: itemsMaster.entrySet()) {
    		shareListcnt++;
            Map < String, String > childListMap = listEntry.getValue();
            holdLastShare.put(shareListcnt, childListMap.get("ShareName").toString());
    	}
    }
}