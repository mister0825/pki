// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2012 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---

package com.netscape.cmstools.cert;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import com.netscape.certsrv.ca.CACertClient;
import com.netscape.certsrv.cert.CertData;
import com.netscape.certsrv.cert.CertRequestInfo;
import com.netscape.certsrv.cert.CertRevokeRequest;
import com.netscape.certsrv.dbs.certdb.CertId;
import com.netscape.certsrv.request.RequestStatus;
import com.netscape.cmstools.ca.CACertCLI;
import com.netscape.cmstools.cli.CLI;
import com.netscape.cmstools.cli.MainCLI;

import netscape.security.x509.RevocationReason;

/**
 * @author Endi S. Dewata
 */
public class CertHoldCLI extends CLI {

    public CACertCLI certCLI;

    public CertHoldCLI(CACertCLI certCLI) {
        super("hold", "Place certificate on-hold", certCLI);
        this.certCLI = certCLI;

        createOptions();
    }

    public void printHelp() {
        formatter.printHelp(getFullName() + " <Serial Number> [OPTIONS...]", options);
    }

    public void createOptions() {
        Option option = new Option(null, "comments", true, "Comments");
        option.setArgName("comments");
        options.addOption(option);

        options.addOption(null, "force", false, "Force");
    }

    public void execute(String[] args) throws Exception {
        // Always check for "--help" prior to parsing
        if (Arrays.asList(args).contains("--help")) {
            printHelp();
            return;
        }

        CommandLine cmd = parser.parse(options, args);

        String[] cmdArgs = cmd.getArgs();

        if (cmdArgs.length != 1) {
            throw new Exception("Missing Serial Number.");
        }

        CertId certID = new CertId(cmdArgs[0]);
        CACertClient certClient = certCLI.getCertClient();
        CertData certData = certClient.reviewCert(certID);

        if (!cmd.hasOption("force")) {

            System.out.println("Placing certificate on-hold:");

            CACertCLI.printCertData(certData, false, false);
            if (verbose) System.out.println("  Nonce: " + certData.getNonce());

            System.out.print("Are you sure (Y/N)? ");
            System.out.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String line = reader.readLine();
            if (!line.equalsIgnoreCase("Y")) {
                return;
            }
        }

        CertRevokeRequest request = new CertRevokeRequest();
        request.setReason(RevocationReason.CERTIFICATE_HOLD);
        request.setComments(cmd.getOptionValue("comments"));
        request.setNonce(certData.getNonce());

        CertRequestInfo certRequestInfo = certClient.revokeCert(certID, request);

        if (verbose) {
            CACertCLI.printCertRequestInfo(certRequestInfo);
        }

        if (certRequestInfo.getRequestStatus() == RequestStatus.COMPLETE) {
            if (certRequestInfo.getOperationResult().equals(CertRequestInfo.RES_ERROR)) {
                String error = certRequestInfo.getErrorMessage();
                if (error != null) {
                    System.out.println(error);
                }
                MainCLI.printMessage("Could not place certificate \"" + certID.toHexString() + "\" on-hold");
            } else {
                MainCLI.printMessage("Placed certificate \"" + certID.toHexString() + "\" on-hold");
                certData = certClient.getCert(certID);
                CACertCLI.printCertData(certData, false, false);
            }
        } else {
            MainCLI.printMessage("Request \"" + certRequestInfo.getRequestId() + "\": "
                    + certRequestInfo.getRequestStatus());
        }
    }
}