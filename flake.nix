{
  description = "Provision a dev environment";

  inputs = {
    typelevel-nix.url = "github:typelevel/typelevel-nix";
    nixpkgs.follows = "typelevel-nix/nixpkgs";
    flake-utils.follows = "typelevel-nix/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, typelevel-nix }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          overlays = [ typelevel-nix.overlays.default ];
        };

        wit-bindgen-scala = pkgs.rustPlatform.buildRustPackage {
          pname = "wit-bindgen";
          name = "wit-bindgen";
          #phases = [ "buildPhase" "installPhase" ];
          src = pkgs.fetchFromGitHub {
            owner = "scala-wasm";
            repo = "wit-bindgen";
            rev = "4f740e9e767e0e1f50f87c708aa50a3970a519de";
            hash = "sha256-WfAgCFkAoPwi+SDZZYhXzh5UKahwWT902KL+7Qi6yBo=";
          };
         cargoHash = "sha256-bAs+j5HJkJ5j6ZZBzfPDbMeu96c92VDbrIt+QKBPofU=";
        };

        mkShell = jdk: pkgs.devshell.mkShell {
          imports = [ typelevel-nix.typelevelShell ];
          name = "cats-effect";
          typelevelShell = {
            jdk.package = jdk;
            nodejs.enable = true;
            native.enable = true;
            nodejs.package = pkgs.nodejs_25;
          };
          packages = with pkgs; [
            wasm-tools
            wasmtime
            wkg
            wac-cli
            wit-bindgen-scala
          ];
        };
      in
      rec {
        devShell = mkShell pkgs.jdk8;

        devShells = {
          "temurin@8" = mkShell pkgs.temurin-bin-8;
          "temurin@11" = mkShell pkgs.temurin-bin-11;
          "temurin@17" = mkShell pkgs.temurin-bin-17;
        };
      }
    );
}
