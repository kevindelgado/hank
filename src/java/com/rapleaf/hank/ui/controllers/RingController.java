package com.rapleaf.hank.ui.controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.rapleaf.hank.coordinator.Coordinator;
import com.rapleaf.hank.coordinator.DomainConfig;
import com.rapleaf.hank.coordinator.HostConfig;
import com.rapleaf.hank.coordinator.HostDomainConfig;
import com.rapleaf.hank.coordinator.HostDomainPartitionConfig;
import com.rapleaf.hank.coordinator.PartDaemonAddress;
import com.rapleaf.hank.coordinator.RingConfig;
import com.rapleaf.hank.coordinator.RingGroupConfig;
import com.rapleaf.hank.ui.URLEnc;

public class RingController extends Controller {

  private final Coordinator coordinator;

  public RingController(String name, Coordinator coordinator) {
    super(name);
    this.coordinator = coordinator;
    actions.put("add_host", new Action() {
      @Override
      protected void action(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doAddHost(req, resp);
      }
    });

    actions.put("delete_host", new Action() {
      @Override
      protected void action(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doDeleteHost(req, resp);
      }
    });

    actions.put("assign_all", new Action() {
      @Override
      protected void action(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        doAssignAll(req, resp);
      }
    });
  }

  protected void doAssignAll(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    RingGroupConfig rgc = coordinator.getRingGroupConfig(req.getParameter("g"));
    int ringNum = Integer.parseInt(req.getParameter("n"));
    RingConfig ringConfig = rgc.getRingConfig(ringNum);

    for (DomainConfig dc : rgc.getDomainGroupConfig().getDomainConfigs()) {
      Integer domainId = rgc.getDomainGroupConfig().getDomainId(dc.getName());

      Set<Integer> unassignedParts = new HashSet<Integer>();
      for (int i = 0; i < dc.getNumParts(); i++) {
        unassignedParts.add(i);
      }

      for (HostConfig hc : ringConfig.getHosts()) {
        HostDomainConfig hdc = hc.getDomainById(domainId);
        if (hdc == null) {
          hdc = hc.addDomain(domainId);
        }
        for (HostDomainPartitionConfig hdpc : hdc.getPartitions()) {
          unassignedParts.remove(hdpc.getPartNum());
        }
      }

      List<Integer> randomizedUnassigned = new ArrayList<Integer>();
      randomizedUnassigned.addAll(unassignedParts);
      Collections.shuffle(randomizedUnassigned);

      List<HostConfig> hosts = new ArrayList<HostConfig>(ringConfig.getHosts());
      for (int i = 0; i < unassignedParts.size(); i++) {
        hosts.get(i % hosts.size()).getDomainById(domainId).addPartition(i, rgc.getDomainGroupConfig().getLatestVersion().getVersionNumber());
      }
    }

    resp.sendRedirect(String.format("/ring.jsp?g=%s&n=%d", req.getParameter("g"), ringNum));
  }

  protected void doDeleteHost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    RingGroupConfig rgc = coordinator.getRingGroupConfig(req.getParameter("g"));
    RingConfig ringConfig = rgc.getRingConfig(Integer.parseInt(req.getParameter("n")));
    ringConfig.removeHost(PartDaemonAddress.parse(URLEnc.decode(req.getParameter("h"))));

    resp.sendRedirect(String.format("/ring.jsp?g=%s&n=%d", rgc.getName(), ringConfig.getRingNumber()));
  }

  private void doAddHost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String rgName = req.getParameter("rgName");
    int ringNum = Integer.parseInt(req.getParameter("ringNum"));
    String hostname = req.getParameter("hostname");
    int portNum = Integer.parseInt(req.getParameter("port"));
    coordinator.getRingGroupConfig(rgName).getRingConfig(ringNum).addHost(new PartDaemonAddress(hostname, portNum));
    resp.sendRedirect("/ring.jsp?g=" + rgName + "&n=" + ringNum);
  }
}
